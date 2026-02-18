# Investigation Results: C4FM Dispatch Tone Corruption

**Date**: 2026-02-17 (updated 2026-02-18)
**Branch**: `013-fix-c4fm-audio-corruption`
**Status**: ROOT CAUSE IDENTIFIED — Sync guard placeholder cascade prevents recovery from sample drops

## Executive Summary

The `syncDetected()` guard added in the upstream merge (commit `de0e722f`) blocks ALL sync detections
during message assembly — including during speculative PLACEHOLDER assembly. When tuner sample drops
corrupt the demodulator state, the guard prevents the framer from re-synchronizing because placeholder
assemblers (created speculatively after NID decode failures) continuously block sync acceptance. Recovery
requires a sync to arrive in a narrow ~12ms window between placeholder dispatches (~3% probability per
message boundary), leading to 10+ seconds of corrupted audio — enough to garble an entire call.

**Fix**: Allow sync detection during PLACEHOLDER assembly while continuing to block syncs during real
message assembly (LDU, HDU, TDU, etc.). This preserves the audio squeak fix while enabling fast recovery
from sample drops.

## Root Cause: Placeholder Cascade

### The Mechanism

1. **Sample drop occurs** — USB buffer overflow, CPU contention (especially after playback refactor's 19ms timer)
2. **Demodulator state corrupted** — No dropped-sample detection exists in the decoder
3. **Assembler fills with garbage** — Corrupted symbols produce invalid message data
4. **Assembler dispatches** → `mMessageAssembler = null` (brief ~12ms window)
5. **Placeholder assembler created** at dibit 57 (speculative, expects 1568 bits = ~191ms)
6. **Sync guard blocks ALL syncs** during placeholder assembly
7. **Placeholder fills and dispatches** (discarded as PLACEHOLDER DUID)
8. **New placeholder created** → syncs blocked again → repeat steps 5-8
9. **Recovery only when sync arrives in ~12ms window** between placeholder dispatch and creation

### Recovery Probability

- Window: ~12ms every ~191ms (57 dibits / 4800 symbols/sec)
- P25 sync patterns arrive every ~360ms (LDU boundary)
- Per-boundary recovery probability: ~12/360 = 3.3%
- Expected recovery attempts: ~30 (average ~10.8 seconds)
- For a 5-6 second dispatch tone call: **entire call is garbled**

### Pre-Merge Behavior (No Guard)

Without the guard, sync detection is accepted at any time. When samples are dropped:
1. Corrupted assembler fills with garbage
2. Next legitimate sync is detected → `nidDetected()` force-completes corrupt assembler
3. New assembler starts fresh from the correct sync position
4. **Recovery within 1 message boundary** (~360ms)

### Evidence

| Metric | Pre-Fix (Guard ON) | Post-Fix (Placeholder Exception) |
|--------|-------------------|----------------------------------|
| Derry C4FM LDUs | 666 | **666** (unchanged) |
| Derry C4FM Sync Blocked | 21 | **0** |
| ROC W LSM v2 LDUs | 434 | **440** (+6 gain) |
| ROC W LSM v2 Regressions | 0 | **0** |
| Derry LSM Sync Blocked | 1,515 | **270** |
| Derry LSM v2 Sync Blocked | 6,147 | **1,240** |

All 21 C4FM blocked syncs were during PLACEHOLDER assembly — the guard was blocking
recovery-enabling syncs, not protecting real message assembly.

## Investigation Process

### Phase 1: Sync Guard Impact (Zero LDU Delta)

The offline test showed the sync guard has zero effect on C4FM LDU count because the test
uses continuous WAV data with no sample drops. The guard blocks 21 syncs, but all are
during PLACEHOLDER assembly (false mid-message syncs that would fail NID anyway).

### Phase 2: Pre-Merge vs Post-Merge Comparison

IMBE frames are **byte-identical** pre-merge and post-merge for the same baseband input.
This confirmed the decode algorithm itself is unchanged — the issue is in frame recovery.

### Phase 3: Frequency Analysis (Key Breakthrough)

| Source | Tone 1 Freq | Tone 2 Freq | RMS |
|--------|------------|------------|-----|
| Offline test (no-reset) | **344 Hz** | **917 Hz** | 0.46 |
| Offline test (per-LDU reset) | **344 Hz** | **917 Hz** | 0.37 |
| Derry FD recording (SDRTrunk) | 300→150 Hz | 150-250 Hz | 0.27 |
| User bad_tone.mp3 | 150-250 Hz | 150-250 Hz | 0.09 |

The offline test produces **correct tones** (344/917 Hz) from the same baseband that
SDRTrunk's real-time recording decoded as garbled 150/250/300 Hz noise. This proved the
corruption is in the real-time pipeline, not the decode algorithm.

### Phase 4: JMBE Codec State Analysis

Per-LDU codec reset testing showed the JMBE codec has significant inter-frame state
affecting amplitude (4-5 frame warm-up), but **not frequency**. Codec state issues
cannot explain the 150 Hz vs 344 Hz frequency difference.

### Phase 5: Pipeline Architecture Analysis

- Message flow is **synchronous** from decoder to audio module (no threading boundary)
- No buffer queue exists in the decoder — samples are processed immediately
- **No dropped-sample detection** exists in P25P1DecoderC4FM
- The decoder processes samples identically regardless of chunk size
- **But**: timestamp continuity is not validated — dropped samples silently corrupt state

### Phase 6: Assembler Lifecycle Discovery (Root Cause)

The P25P1MessageAssembler:
- Has a fixed target size based on DUID (LDU: 1568 bits, HDU: 658 bits)
- `isComplete()` is a simple capacity check: `getMessage().isFull()`
- Can get "stuck" if sync is blocked and no `forceCompletion()` is called
- PLACEHOLDER assemblers expect 1568 bits (same as LDU) and are discarded on dispatch
- **PLACEHOLDER assembly continuously blocks sync detection via the guard**

## The Fix

### Code Change (P25P1MessageFramer.java)

```java
// Before (blocks ALL syncs during ANY assembly):
if(mMessageAssembler == null) {
    mSyncDetected = true;
    ...
}

// After (allows syncs during PLACEHOLDER assembly):
if(mMessageAssembler == null ||
   mMessageAssembler.getDataUnitID() == P25P1DataUnitID.PLACE_HOLDER) {
    mSyncDetected = true;
    ...
}
```

### Why This Is Safe

1. **Audio squeak fix preserved**: Real message assembly (LDU, HDU, TDU, etc.) still blocks syncs
2. **PLACEHOLDER messages are discarded**: Force-completing a placeholder via sync has no audio impact
3. **NID validation provides secondary filtering**: False syncs during placeholder assembly fail NID decode
4. **Regression tested**: 0 LDU delta on C4FM, +6 LDU gain on LSM v2, 0 regressions

### Contributing Factor: Playback Refactor CPU Impact

The upstream merge's playback refactor (100ms → 19ms processing interval) may increase the
frequency of sample drops by increasing CPU contention. This makes the sync guard cascade
more likely to occur, explaining why the issue appears as a regression from the merge.

## Regression Test Results

### Derry FD C4FM (Gold Standard: 666 LDUs)

| Metric | Before Fix | After Fix | Delta |
|--------|-----------|-----------|-------|
| C4FM LDUs | 666 | 666 | 0 |
| C4FM Sync Blocked | 21 | 0 | -21 |
| C4FM NID Success | 100% | 100% | 0 |

### ROC W LSM v2 (Gold Standard: 434 LDUs)

| Metric | Before Fix | After Fix | Delta |
|--------|-----------|-----------|-------|
| LSM v2 LDUs | 434 | 440 | **+6** |
| LSM v2 Regressions | 0 | 0 | 0 |

## Files Modified

### P25P1MessageFramer.java (Fix)
- Modified `syncDetected()` to allow sync detection during PLACEHOLDER assembly
- Preserves audio squeak fix for real message assembly

### DerryAudioRegressionTest.java (Test Infrastructure)
- Standalone test for baseband decode + JMBE + MP3 output
- Supports guard ON/OFF comparison and codec reset modes

### build.gradle (Test Infrastructure)
- Added `runDerryAudioRegression` Gradle task

## Deliverables

| File | Description |
|------|-------------|
| `comparison/fixed_guard/tone_guard_on.mp3` | Post-fix decode (guard ON with placeholder exception) |
| `comparison/fixed_guard/tone_guard_off.mp3` | Post-fix decode (guard OFF) |
| `comparison/perldu-reset/tone_guard_off.mp3` | Per-LDU codec reset test |
| `_SAMPLES/Derry FD/segments/bad_tone_segment.wav` | 60s baseband around bad tone (~15695s) |
| `_SAMPLES/Derry FD/segments/good_tone_segment.wav` | 60s baseband around good tone (~4526s) |

## Recommendations

1. **Live verification needed**: Run SDRTrunk with this fix on the Derry FD channel and verify
   dispatch tones are clean. The offline test cannot reproduce sample drops.

2. **Consider adding dropped-sample detection** to P25P1DecoderC4FM:
   - Compare timestamp deltas with expected sample counts
   - Log or broadcast DroppedSamplesMessage when gaps are detected
   - This would provide visibility into the frequency of sample drops

3. **Monitor playback refactor CPU impact**: If sample drops are frequent, the 19ms timer
   may need optimization or the stall detection timeout may need adjustment.
