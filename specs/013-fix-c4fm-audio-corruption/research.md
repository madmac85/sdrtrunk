# Research: C4FM Audio Corruption Root Cause

**Date**: 2026-02-17
**Feature**: 013-fix-c4fm-audio-corruption

## Decision 1: Root Cause Identification

**Decision**: The audio corruption is caused by a sync detection race condition in `P25P1MessageFramer.syncDetected()`.

**Rationale**: The upstream audio squeak fix (commit `de0e722f`) added `if(mMessageAssembler == null)` guard that blocks ALL sync detections when a message assembler exists. At P25 message boundaries, the assembler is complete (`isComplete() == true`) but not yet dispatched (dispatch occurs on the next `symbol()` call). This creates a race condition where legitimate frame syncs are silently dropped, preventing message transitions.

**Alternatives Considered**:
- Audio playback refactor (27 files changed) — ruled out because the playback path doesn't affect decode, and the issue is protocol-level (garbled IMBE frames, not playback artifacts)
- C4FM demodulator changes — ruled out because `P25P1DecoderC4FM.java` and `P25P1DemodulatorC4FM.java` were NOT changed in the merge
- IMBE codec issue — ruled out because the good tone demonstrates IMBE can faithfully reproduce tonal content; the bad tone has broadband artifacts characteristic of corrupted input frames

## Decision 2: Fix Approach

**Decision**: Modify `syncDetected()` to dispatch complete-but-undispatched messages before checking the guard.

**Rationale**: This is the minimal fix that resolves the race condition while preserving the audio squeak protection. Adding `if(mMessageAssembler != null && mMessageAssembler.isComplete()) { dispatchMessage(); }` before the guard ensures legitimate syncs at message boundaries pass through, while false syncs during incomplete assembly are still blocked.

**Alternatives Considered**:
- Remove the guard entirely (revert to pre-merge) — rejected because it re-introduces the audio squeak bug
- Add `|| mMessageAssembler.isComplete()` to the guard condition — works but leaves the assembler in a complete-but-not-dispatched state, which is less clean
- Add a timeout-based assembler cleanup — over-engineered for this specific race condition
- Add a flag to toggle guard behavior — adds unnecessary complexity for testing

## Decision 3: Audio Comparison Test Approach

**Decision**: Create a test that processes a segment of the large baseband file, decodes IMBE to PCM via JMBE, and writes MP3 output. Use a method-level toggle to simulate pre-merge behavior for comparison.

**Rationale**: A git worktree approach requires building the entire project at two commits, which is slow and fragile. Instead, since the only change is in `syncDetected()`, we can add a `setSyncGuardEnabled(boolean)` test helper or directly invoke `syncDetectedUnguarded()` for pre-merge comparison.

**Alternatives Considered**:
- Git worktree with separate build — too slow, fragile
- Manual checkout and rebuild — user would need to do this manually
- Recording baseline metrics only (no audio output) — doesn't meet the spec requirement for MP3 deliverables

## Key Technical Findings

### P25P1 Message Assembly Lifecycle

1. **Sync detected** → `mSyncDetected = true`, `mNIDPointer = 0`
2. **NID collected** (33 dibits) → `checkNID()` validates
3. **NID valid** → `nidDetected()` → creates `P25P1MessageAssembler`
4. **Message assembly** → assembler receives dibits until `isComplete()`
5. **Dispatch** → `dispatchMessage()` → `mMessageAssembler = null`

### Message Durations

| Type | Dibits | Duration (ms) |
|------|--------|--------------|
| HDU | 253 | 52.7 |
| LDU1/LDU2 | 841 | 175.2 |
| TDU | 85 | 17.7 |
| TSBK | 253 | 52.7 |

### The Race Window

When the C4FM demodulator detects a sync:
1. All body symbols have already been passed to the framer (via the symbol delay line)
2. The assembler should be complete
3. But `dispatchMessage()` hasn't been called yet (it fires on the next `symbol()` call)
4. So `mMessageAssembler != null` even though the message is done
5. The guard blocks the sync

### Audio Analysis

| Property | Good Tone | Bad Tone |
|----------|-----------|----------|
| Frequencies | 344 Hz + 914 Hz | Broadband 50 Hz harmonics |
| RMS | -6.4 dBFS | -22.4 dBFS |
| Spectral flatness | 0.046 (tonal) | 0.058 (noise-like) |
| Diagnosis | Clean IMBE decode of tones | IMBE decode of corrupted frames |

The bad tone's 50 Hz harmonic comb is characteristic of IMBE codec output when fed random/corrupted bit patterns — confirming the issue is corrupted voice frame data reaching the codec, not a codec or playback problem.
