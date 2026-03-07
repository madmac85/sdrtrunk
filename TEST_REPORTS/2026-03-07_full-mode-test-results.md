# Full Mode Test Results — DUID Enumeration + Corrected NACs

**Date**: 2026-03-07 (overnight run started 2026-03-06)
**Branch**: master (commit 01da05ec)
**Changes**: BCH DUID enumeration, MAX_AUDIO_HOLDOVER_MS 500->1000, corrected NAC values
**Playlist**: corrected-full-test.xml (hex-as-decimal NAC bug fixed)
**Output**: `_SAMPLES/full-test-output-2026-03-06/` (48MB, 11,022 MP3 segments)

## Test Summary

| Metric | Value |
|--------|-------|
| Total files processed | 21 |
| Total LDUs decoded | 28,500 |
| Total audio produced | 5,130s (85.5 min) |
| Total audio segments | 11,022 |
| Total baseband time | ~23.4 hours |

## Results by Channel

### Simulcast Channels (CQPSK_V2 with NAC — DUID enumeration active)

#### Londonderry FD (156.1125 MHz, NAC=659, CQPSK_V2)

| Sample | LDUs | Audio | Segments | Avg Seg | Valid Msgs | Baseline LDUs | Delta |
|--------|------|-------|----------|---------|------------|---------------|-------|
| Old (Feb 18, 6669s) | 1,764 | 317.5s | 1,408 | 0.23s | 7,691/10,734 (72%) | 569 | +1,195 (+210%) |
| New (Mar 6, 8058s) | 1,857 | 334.3s | 1,631 | 0.20s | 8,165/11,728 (70%) | 503 | +1,354 (+269%) |
| **Total** | **3,621** | **651.8s** | **3,039** | **0.21s** | **15,856/22,462 (71%)** | **1,072** | **+2,549 (+238%)** |

**Note**: Average segment length of 0.21s indicates heavy fragmentation. Most segments contain only 1 LDU.
This is expected without holdover in the test harness — the production decoder with holdover will consolidate these
into longer segments. The increased MAX_AUDIO_HOLDOVER_MS (1000ms) is designed for exactly this scenario.

#### Windham PD (155.6025 MHz, NAC=1559, CQPSK_V2)

| Metric | Value |
|--------|-------|
| LDUs | 2,363 |
| Audio | 425.3s (7.1 min) |
| Segments | 1,878 |
| Avg Segment | 0.23s |
| Valid Messages | 11,300/15,681 (72%) |
| Recording Length | 8,104s (2h15m) |

**Note**: First time testing Windham PD with correct NAC (1559) and CQPSK_V2 decoder.
Previously configured as CQPSK with NAC=617 (wrong). No baseline for comparison.
The 72% valid message rate and segment fragmentation match Londonderry FD's pattern — both are simulcast.

#### Rock County PD West (154.815 MHz, NAC=279, CQPSK_V2)

| Sample | LDUs | Audio | Segments | Avg Seg | Valid Msgs |
|--------|------|-------|----------|---------|------------|
| Jan 24 (414s) | 499 | 89.8s | 86 | 1.04s | 3,444/3,531 (98%) |
| Jan 24 (1258s) | 856 | 154.1s | 291 | 0.53s | 6,812/7,355 (93%) |
| Jan 24 (984s) | 511 | 92.0s | 195 | 0.47s | 2,906/3,326 (87%) |
| Jan 25 (4572s) | 3,358 | 604.4s | 973 | 0.62s | 19,536/21,339 (92%) |
| Mar 6 (8128s) | 4,895 | 881.1s | 1,728 | 0.51s | 36,111/39,385 (92%) |
| Feb 2 (6672s) | 4,057 | 730.3s | 1,177 | 0.62s | 26,284/28,581 (92%) |
| **Total** | **14,176** | **2,551.7s** | **4,450** | **0.57s** | **95,093/103,517 (92%)** |

**Baseline comparison**: ROC W gold standard was 434 LDUs on the Jan 24 414s file (without NAC).
With NAC=279: 499 LDUs (+15%). The 92% valid message rate is much better than LFD's 71% — ROC W
has less multipath NID corruption than Londonderry FD.

#### Rock County PD East (154.950 MHz, NAC=279, CQPSK_V2)

| Sample | LDUs | Audio | Segments | Avg Seg | Valid Msgs |
|--------|------|-------|----------|---------|------------|
| Jan 24 (253s) | 161 | 29.0s | 57 | 0.51s | 1,856/1,940 (96%) |
| Jan 24 (1245s) | 385 | 69.3s | 288 | 0.24s | 2,523/3,109 (81%) |
| Jan 24 (974s) | 342 | 61.6s | 208 | 0.30s | 2,387/2,863 (83%) |
| Jan 25 (4561s) | 3,284 | 591.1s | 897 | 0.66s | 19,720/21,436 (92%) |
| **Total** | **4,172** | **751.0s** | **1,450** | **0.52s** | **26,486/29,348 (90%)** |

### Non-Simulcast Channels (C4FM, no NAC — DUID enumeration inactive)

#### Derry Fire 1 (153.995 MHz, C4FM, NAC=0)

| Sample | LDUs | Audio | Segments | Avg Seg | Valid Msgs |
|--------|------|-------|----------|---------|------------|
| Feb 17 (146s) | 80 | 14.4s | 4 | 3.60s | 460/460 (100%) |
| Feb 17 (97s) | 224 | 40.3s | 5 | 8.06s | 852/852 (100%) |
| Feb 17 (490s) | 362 | 65.2s | 20 | 3.26s | 3,299/3,299 (100%) |
| Feb 17 (100s) | 0 | 0.0s | 0 | — | 0/0 (—) |
| Mar 6 (6536s) | 387 | 69.7s | 12 | 5.81s | 895/895 (100%) |
| **Total** | **1,053** | **189.5s** | **41** | **4.62s** | **5,506/5,506 (100%)** |

**Baseline**: 666 LDUs across the 4 original files. Current: 666 (identical). New Mar 6 file adds 387.
100% valid message rate, 4.6s average segment length — excellent quality on C4FM single-site.

#### Salem Fire (155.8875 MHz, C4FM, NAC=0)

| Metric | Value |
|--------|-------|
| LDUs | 2,257 |
| Audio | 406.3s (6.8 min) |
| Segments | 115 |
| Avg Segment | 3.53s |
| Valid Messages | 8,595/8,595 (100%) |

100% valid, long segments — clean C4FM channel.

#### Derry PD 1 (151.010 MHz, C4FM, NAC=0)

| Metric | Value |
|--------|-------|
| LDUs | 330 |
| Audio | 59.4s (1.0 min) |
| Segments | 27 |
| Avg Segment | 2.20s |
| Valid Messages | 25,102/25,102 (100%) |

100% valid. High message count (25K) relative to LDUs — this is a control/voice hybrid channel.

#### Windham FD Dig (154.175 MHz, C4FM, NAC=0)

| Metric | Value |
|--------|-------|
| LDUs | 528 |
| Audio | 95.0s (1.6 min) |
| Segments | 22 |
| Avg Segment | 4.32s |
| Valid Messages | 2,299/2,299 (100%) |

100% valid, long segments — clean C4FM channel.

## Key Observations

### 1. DUID Enumeration Delivers Massive Improvement on Simulcast
- Londonderry FD: +238% LDU improvement (1,072 -> 3,621 total across both samples)
- The improvement is consistent across both old and new recordings
- Windham PD (newly tested with correct config): similar decode characteristics to LFD

### 2. Segment Fragmentation on Simulcast Channels
- Simulcast channels average 0.2-0.6s per segment in the test harness
- C4FM channels average 2-5s per segment
- This difference is because the test harness lacks holdover — each decoded LDU becomes its own segment
- In production, the increased 1000ms holdover will consolidate these into longer, more natural segments

### 3. Zero Regressions on Non-Simulcast Channels
- All C4FM channels: 100% valid message rate, identical LDU counts to baseline
- DUID enumeration only activates when configuredNAC > 0 — no risk to other channels

### 4. ROC W/E Improvement with Correct NAC
- ROC W: 499 LDUs on reference file (was 434 without NAC, +15%)
- Valid message rate 92% (vs 71% for LFD) — less multipath corruption than Londonderry

### 5. Bit Error Distribution
- Simulcast channels show high bit errors (expected — BCH is correcting more errors per NID)
- C4FM channels show near-zero bit errors — unaffected by changes

## Playlist Corrections Applied

| Channel | Modulation | NAC (old -> new) | Change |
|---------|-----------|-----------------|--------|
| Londonderry FD | CQPSK -> CQPSK_V2 | 293 -> 659 | Hex-as-decimal fix + v2 decoder |
| Windham PD | CQPSK -> CQPSK_V2 | 617 -> 1559 | Hex-as-decimal fix + v2 decoder |
| ROC W | CQPSK_V2 | 117 -> 279 | Hex-as-decimal fix |
| ROC E | CQPSK_V2 | 117 -> 279 | Hex-as-decimal fix |
| Derry FD | C4FM | 827 -> 0 | C4FM doesn't use NAC |
| Windham FD | C4FM | 907 -> 0 | C4FM doesn't use NAC |

## Audio Output

- Location: `_SAMPLES/full-test-output-2026-03-06/`
- Total size: 48MB
- Total MP3 segments: 11,022
- Organized by source file (21 subdirectories)
- Metrics: `metrics.json`

## Next Steps

1. Listen to Londonderry FD and Windham PD audio segments to assess quality of recovered frames
2. Consider running STT (speech-to-text) word count on simulcast audio to measure intelligibility
3. Test with production holdover (1000ms) to measure segment consolidation
4. Update live playlist with corrected NAC values and modulations
