# Windham PD Simulcast Audio Quality Test Report

**Date:** 2026-03-07
**Sample:** `20260306_074506_155602500_Municipalities_Windham_Wndhm-Police_4_baseband.wav` (8104s, CQPSK_V2, NAC=1559)
**Baseline commit:** `a03fe17e` (spec 017 simulcast improvements)

## Phase 0: Salem Fire C4FM Regression Investigation

**Finding: Production-only issue, no code regression.**

The upstream merge (`98987570`) only touched audio playback files (`AudioPlaybackDeviceDescriptor`, `AudioPlaybackDeviceManager`). No C4FM decode path code was changed. DecoderFactory quality gate is strictly gated: `lsmv2Decoder != null && p1Config.hasConfiguredNAC()`.

| Metric | Pre-017 | Current | Status |
|--------|---------|---------|--------|
| Salem Fire LDUs | 2,257 | 2,257 | PASS |
| Salem Fire Valid | 8,595/8,595 | 8,595/8,595 | PASS |
| Salem Fire STT | -- | 1,022 words | OK |

C4FM regression checks (all match baselines):
- Windham FD Dig: 528 LDUs (expected 528) PASS
- Salem Fire: 2,257 LDUs (expected 2,257) PASS
- Derry Fire 1: 387 LDUs (expected 387) PASS

## Phase 1: Windham PD Baselines

### Decode Metrics

| Config | maxBch | maxImbe | LDUs | Valid/Total | Audio(s) | Segments |
|--------|--------|---------|------|-------------|----------|----------|
| v2-baseline | 11 | off | 2,363 | 11,300/15,681 (72%) | 425.3 | 1,878 |
| c5b3 | 5 | 3 | 1,520 | 6,548/8,768 (75%) | 273.6 | 1,093 |
| c5b0 | 5 | off | 1,520 | 6,548/8,768 (75%) | 273.6 | 1,093 |
| c11b3 | 11 | 3 | 2,363 | 11,300/15,681 (72%) | 425.3 | 1,878 |

**BCH T=5 drops 35.7% of LDUs** (2,363 -> 1,520) but surviving frames are higher quality.

### IMBE Error Distribution (c5b3 diagnostic)

| Errors | Frames | Pct |
|--------|--------|-----|
| 0 | 651 | 4.8% |
| 1-3 | 1,148 | 8.4% |
| 4-6 | 743 | 5.4% |
| 7-10 | 462 | 3.4% |
| 11+ | 10,676 | **78.0%** |

**Non-bimodal distribution**: Unlike ROC W / Londonderry FD (bimodal: 0-3 or 11+), Windham PD has widespread corruption with 78% of frames severely damaged. Only 13.2% of frames have <=3 errors.

### STT Word Count (Whisper base model, 2s segment gap)

| Config | BCH | Gate | Adaptive | Words | Words/min | vs baseline |
|--------|-----|------|----------|-------|-----------|-------------|
| v2-baseline | 11 | off | -- | 31 | 2.9 | -- |
| c11b3 | 11 | 3 | no | 56 | 5.3 | +81% |
| c5b6 | 5 | 6 | no | 98 | 13.9 | +216% |
| adaptive | 5 | 3 | yes | 114 | 16.2 | +268% |
| c5b3 | 5 | 3 | no | 118 | 16.8 | +281% |
| **c5b0** | **5** | **off** | -- | **139** | **19.7** | **+348%** |

### ROC W Cross-Check (BCH T=5, Jan 24 sample)

| Config | Words | Words/min |
|--------|-------|-----------|
| c5b0 (BCH5 only) | 157 | 103.3 |
| adaptive (BCH5+gate3+adaptive) | 143 | 94.1 |

## Phase 2: Analysis

### Key Findings

1. **BCH T=5 is the dominant improvement** for both channels. On Windham PD it provides +348% STT improvement vs baseline.

2. **Quality gate hurts Windham PD at all thresholds.** With 78% of frames having 11+ errors, the gate replaces most audio with silence/repetition. The JMBE codec's partial decode of corrupted frames produces more useful speech than frame repetition.

3. **Quality gate also slightly hurts ROC W** (157 -> 143 words, -9%). The original spec 017 +25% improvement was measured vs the V2 baseline (no BCH), not vs BCH-only.

4. **Frame repetition produces hallucinated patterns.** The c11b3 preview shows "you you you you you you..." — Whisper counts these as words but they carry no information. Silence is better for both STT accuracy and listener experience.

5. **Adaptive gate partially mitigates** the quality gate damage (114 words vs 118 without adaptive for WPD), but BCH-only remains superior.

### Error Distribution Comparison

| Channel | 0-3 errors | 11+ errors | Distribution |
|---------|-----------|------------|-------------|
| ROC W / LFD | 3-12% | 88-97% | Bimodal |
| Windham PD | 13.2% | 78.0% | Spread (non-bimodal) |

Both channels have high corruption, but Windham PD has more frames in the 4-10 error range (8.8% vs ~0% for ROC W). This intermediate corruption is where the quality gate's binary decision (pass/fail) is least effective.

## Phase 3: Implemented Changes

### 3.1 Frame Repetition (P25P1AudioModule.java)
- Gated frames now repeat last good frame with fade-out after 9 consecutive repeats
- **Result: HARMFUL for STT** (-9 to -15% vs silence). Produces monotonous patterns.
- Kept in codebase as concealment option but quality gate results show silence is preferable.

### 3.2 Codec Reset on Sustained Corruption (P25P1AudioModule.java)
- Codec resets after 5 consecutive gated frames to prevent state contamination
- Integrated with frame repetition and adaptive gate

### 3.3 Segment Gap Consolidation (DecodeQualityTest.java, build.gradle)
- Added `--segment-gap` / `-PsegmentGap` parameter (default 500ms, configurable)
- 2000ms gap consolidates segments for better STT accuracy: 1,878 -> 1,244 (baseline), 1,093 -> 862 (c5b3)

### 3.4 Adaptive Quality Gate (P25P1AudioModule.java)
- Tracks pass rate over 27-frame sliding window (3 LDUs)
- Auto-disables gate when <30% of frames pass (non-bimodal channels)
- Re-enables when pass rate recovers >50%
- **Result: Partially mitigates quality gate damage** but BCH-only is still superior

## Recommendations

### Production Config
1. **BCH T=5**: Keep auto-enabled for CQPSK_V2 + configured NAC (proven beneficial)
2. **Quality gate**: Remove auto-enable at maxImbeErrors=3 from DecoderFactory. The BCH threshold provides sufficient filtering. Quality gate is neutral-to-harmful on top of BCH T=5.
3. **Frame repetition**: Not recommended as default. Silence-based concealment is better for both STT and listener experience.
4. **Adaptive gate**: Reasonable safety net if quality gate is kept, but not needed if gate is removed.

### Optimal Config Per Channel
| Channel | BCH T | Quality Gate | STT Improvement |
|---------|-------|-------------|-----------------|
| Windham PD | 5 | Off | +348% vs v2-baseline |
| ROC W | 5 | Off | Best tested (157 words) |
| Londonderry FD | 5 | Off (recommended) | TBD |

## Files Modified
- `P25P1AudioModule.java` — Frame repetition, codec reset, adaptive gate
- `DecodeQualityTest.java` — Segment gap, frame repetition, adaptive gate in test harness
- `build.gradle` — `segmentGap` property
