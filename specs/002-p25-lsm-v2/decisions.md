# Implementation Decisions

## 2026-01-24 - Separate Demodulator Class (Not Modify Existing)
**Context**: Need to fix cold-start issues in LSM for conventional channels while keeping the existing LSM working for trunked channels.
**Decision**: Create a new `P25P1DemodulatorLSMv2` class rather than adding modes/flags to the existing `P25P1DemodulatorLSM`.
**Rationale**: Keeps the existing LSM untouched and risk-free. The user explicitly wants to A/B compare, so both must work independently. A separate class is cleaner than conditional logic throughout the demodulator.
**Alternatives Considered**: Feature flags within existing class (complex, risk of regressions), copy-on-write modifications with inheritance (fragile coupling to parent implementation details).

## 2026-01-24 - New Modulation Enum Entry (Not Replace CQPSK)
**Context**: Need a way for the user to select the v2 demodulator.
**Decision**: Add `CQPSK_V2("Conventional (LSM v2)")` to the Modulation enum. Keep `CQPSK("Simulcast (LSM)")` unchanged.
**Rationale**: Preserves backward compatibility for existing playlists. Users can explicitly opt-in to v2. Jackson deserialization of old playlists with `CQPSK` continues to work.
**Alternatives Considered**: Replace CQPSK entirely (user can't compare), add a separate boolean flag in config (clutters the UI, non-obvious).

## 2026-01-24 - Transmission Boundary via Sample Energy
**Context**: Need to detect when a new PTT transmission starts so we can reset demodulator state.
**Decision**: Monitor incoming sample magnitude. When samples have been below a threshold for >= 200ms and then rise above it, treat this as a transmission boundary and reset state.
**Rationale**: Simple, doesn't require external signaling. Works with the existing I/Q sample stream. 200ms threshold avoids false resets from brief signal fades or inter-burst gaps.
**Alternatives Considered**: Monitor noise squelch state (LSM decoder doesn't have noise squelch), use sync loss as reset trigger (too aggressive, would reset during normal decode errors).

## 2026-01-24 - Adaptive PLL Gain with Acquisition/Tracking Modes
**Context**: Fixed PLL gain of 0.1 is suboptimal for both cold-start (too aggressive on noisy symbols) and steady-state (could be more responsive for frequency tracking).
**Decision**: Use two-stage PLL gain: acquisition mode (first ~50 symbols after reset) with higher bandwidth for frequency offset acquisition, then transition to lower-bandwidth tracking mode.
**Rationale**: Standard practice in PLL design. Acquisition mode tolerates the initial noisy symbols while quickly locking frequency. Tracking mode provides stable, low-jitter phase estimates.

## 2026-01-24 - Gardner TED Suppression on Cold Start
**Context**: Gardner TED uses previous and current symbol values. On cold start, previous values are stale/zero, producing erroneous timing corrections.
**Decision**: Suppress TED output (use nominal timing) for the first 2 symbol periods after a reset. Begin TED operation only after valid previous/current symbol pairs are established.
**Rationale**: Prevents the timing loop from being pulled off-center by invalid data. Two symbols is the minimum needed to establish valid Gardner TED inputs.
