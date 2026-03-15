# Tasks: Fix Trailing Digital Static on C4FM Transmissions

**Feature Branch**: `023-fix-trailing-static`
**Plan**: [plan.md](plan.md)

## Task 1: Add DUID correction flag to P25P1Message
- [ ] Add `private boolean mDuidCorrected = false` field
- [ ] Add `setDuidCorrected(boolean)` setter
- [ ] Add `isDuidCorrected()` getter
- [ ] Verify build compiles

## Task 2: Mark corrected messages in P25P1MessageFramer
- [ ] In `dispatchOther()`, after message creation, check `mConsecutiveDuidCorrections > 0`
- [ ] If corrected, call `message.setDuidCorrected(true)` on the created message
- [ ] Verify build compiles

## Task 3: Suppress audio for corrected LDUs in P25P1AudioModule
- [ ] In `processAudio(LDUMessage ldu)`, check `ldu.isDuidCorrected()`
- [ ] If corrected: apply rapid fade using last good frame (2 frames / 40ms), then silence
- [ ] If not corrected: process audio normally (existing behavior)
- [ ] Verify build compiles

## Task 4: Run regression tests
- [ ] Run waveform analysis on all 4 channels (ROC W, LFD, Hudson, Derry)
- [ ] Verify ROC W metrics identical to baseline
- [ ] Verify C4FM quality score >= baseline
- [ ] Verify trailing static RMS < 0.002 in last 250ms

## Task 5: Commit and push
- [ ] Commit changes
- [ ] Push to fork
