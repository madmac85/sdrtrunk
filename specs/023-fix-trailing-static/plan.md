# Implementation Plan: Fix Trailing Digital Static on C4FM Transmissions

**Feature Branch**: `023-fix-trailing-static`
**Created**: 2026-03-14
**Spec**: [spec.md](spec.md)

## Problem Analysis

After the holdover fix (DUID correction limit=3 for C4FM), 1-3 fake LDUs are still decoded before the limit kicks in. The IMBE codec decodes noise input from these fake LDUs as low-amplitude digital static (~250ms, RMS ~0.004, spectral energy ~2800Hz).

**Root cause**: The DUID correction in `P25P1MessageFramer.nidDetected()` changes TDU→LDU and increments `mConsecutiveDuidCorrections`, but the resulting LDU message carries **no flag** indicating it was corrected. By the time the message reaches `P25P1AudioModule.processAudio()`, there's no way to distinguish a corrected (noise-derived) LDU from a genuine voice LDU.

## Approach: Flag-and-Suppress

**Strategy**: Propagate a "DUID-corrected" flag from the framer to the audio module via the message object, then suppress audio for flagged messages.

**Why this approach over alternatives**:
- **Reducing DUID limit to 0**: Eliminates all corrections including legitimate mid-call ones. Too aggressive.
- **Reducing DUID limit to 1**: Still produces ~180ms of noise (one full LDU = 9 IMBE frames).
- **Post-codec RMS detection**: Risk of false-positive suppression on quiet speech. Threshold tuning is fragile.
- **Flag-and-suppress**: Precise — only noise-derived frames are suppressed. Zero false-positive risk on genuine voice.

## Architecture

```
P25P1MessageFramer.nidDetected()
  ↓ mConsecutiveDuidCorrections > 0 when correction occurs
P25P1MessageFramer.dispatchOther()
  ↓ P25MessageFactory.create() → P25P1Message
  ↓ if correction: message.setDuidCorrected(true)
  ↓ broadcast(message)
  ↓ ... listener chain ...
P25P1AudioModule.processAudio(LDUMessage ldu)
  ↓ if ldu.isDuidCorrected(): apply fade-to-silence instead of codec decode
```

## Changes

### 1. P25P1Message — Add DUID correction flag
- Add `private boolean mDuidCorrected = false`
- Add `public void setDuidCorrected(boolean corrected)`
- Add `public boolean isDuidCorrected()`

### 2. P25P1MessageFramer — Mark corrected messages
- In `dispatchOther()`: after `P25MessageFactory.create()`, check `mConsecutiveDuidCorrections > 0` and call `message.setDuidCorrected(true)`
- Also mark in `dispatchTDU()` — not needed since TDU doesn't reach audio module, but for completeness

### 3. P25P1AudioModule — Suppress audio for corrected LDUs
- In `processAudio(LDUMessage ldu)`: if `ldu.isDuidCorrected()`, apply fade-to-silence:
  - First corrected LDU after voice: rapid 2-frame fade-out (40ms) using last good frame
  - Subsequent corrected LDUs: output silence
  - This produces a smooth transition from voice→silence with no abrupt cut

### 4. DecodeQualityTest — Propagate flag through test infrastructure
- Ensure the test harness respects the flag for accurate regression metrics

## Testing

1. **Waveform analysis**: Last 250ms of decoded transmissions should have RMS < 0.002
2. **STT regression**: C4FM channels (Derry, LFD, Hudson) within 2% of baseline word count
3. **CQPSK regression**: ROC W metrics identical to baseline
4. **Listening test**: Sample file `_SAMPLES/rocnh-7-1773539883.mp3` — compare before/after

## Files Modified

| File | Change |
|------|--------|
| `P25P1Message.java` | Add `mDuidCorrected` field + getter/setter |
| `P25P1MessageFramer.java` | Mark messages with `setDuidCorrected(true)` in dispatch |
| `P25P1AudioModule.java` | Suppress/fade audio for corrected LDUs |
