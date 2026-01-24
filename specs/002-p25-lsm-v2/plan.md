# Implementation Plan: P25 LSM v2 Demodulator

## Architecture

The implementation adds a new demodulator/decoder pair alongside the existing LSM implementation. The key architectural decisions:

1. **New classes, not modifications**: `P25P1DemodulatorLSMv2` and `P25P1DecoderLSMv2` are separate from the existing LSM classes. This ensures zero risk to existing functionality and enables A/B comparison.

2. **Same pipeline position**: The v2 decoder slots into the same position in the processing chain as the existing LSM decoder — after decimation, baseband filtering, and pulse shaping.

3. **Enum-based selection**: A new `CQPSK_V2` entry in the `Modulation` enum drives both the UI selection and the `DecoderFactory` instantiation logic.

## Key Design Decisions

### Transmission Boundary Detection

The v2 demodulator tracks sample energy using an exponential moving average. When energy has been below a threshold for >= 200ms (960 symbols at 4800 sym/sec) and then rises above it, a cold-start reset is triggered. This is tracked as a sample counter within the demodulator's `process()` method.

### Adaptive PLL

Two-stage PLL gain:
- **Acquisition** (first 50 symbols after reset): `pllGain = 0.25f` — wider bandwidth for quick frequency capture
- **Tracking** (steady state): `pllGain = 0.05f` — narrow bandwidth for stable tracking

Transition is symbol-count based: after 50 symbols, switch to tracking mode.

### Cold-Start Differential State

On reset:
- `previousSymbolI = 0, previousSymbolQ = 0`
- `previousMiddleI = 0, previousMiddleQ = 0`
- `previousCurrentI = 0, previousCurrentQ = 0`
- A `mFirstSymbol` flag skips the first differential demodulation output (which would use zero-valued previous state)

### Fast AGC

On reset:
- AGC factor starts at `0.2f` (20% per symbol)
- After 20 symbols, tapers to the standard `0.05f`
- `sampleGain` resets to `1.0f`

### Gardner TED Suppression

A symbol counter tracks symbols since last reset. For the first 2 symbols, timing adjustment is forced to zero (nominal `samplesPerSymbol` spacing used).

## Files to Create

| File | Purpose |
|------|---------|
| `P25P1DemodulatorLSMv2.java` | V2 demodulator with cold-start improvements |
| `P25P1DecoderLSMv2.java` | Decoder wiring the v2 demodulator |

## Files to Modify

| File | Change |
|------|--------|
| `Modulation.java` | Add `CQPSK_V2` enum entry |
| `DecoderFactory.java` | Add `case CQPSK_V2` |
| `P25P1ConfigurationEditor.java` | Add "LSM v2" toggle button, update save/load logic |
