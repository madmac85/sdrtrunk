# Research: Simulcast Audio Quality Improvements v2

## R1: Silence Detection Threshold

**Decision**: RMS threshold of 0.01 with minimum 100ms (5 IMBE frames) duration.

**Rationale**: IMBE codec output for decoded voice has RMS typically 0.1-0.9 (after 5x gain in test harness). Ambient noise on open mic produces RMS ~0.02-0.05. Decode failure silence (quality-gated or codec-reset frames) produces exact zeros or near-zero RMS (<0.001). A threshold of 0.01 cleanly separates decode-failure silence from ambient noise. The 100ms minimum prevents counting natural inter-word pauses (typically 50-80ms) as decode failures.

**Alternatives considered**:
- Peak amplitude instead of RMS: Rejected — RMS better represents sustained silence vs single-sample spikes
- Zero-crossing rate: Rejected — more complex, no advantage over RMS for this use case
- Spectral analysis: Rejected — overkill for detecting silence, much slower

## R2: Training-Assisted LMS vs Retroactive Sync Training

**Decision**: Prospective NID training (use known NAC symbols as training during NID field), not retroactive sync training.

**Rationale**: Retroactive sync training would require storing raw samples and re-processing them after sync detection — adding latency, memory, and complexity. Prospective NID training exploits the fact that when NAC is configured, the first 6 dibits (12 bits) of the NID after sync are the known NAC. This gives 6 × ~4 = 24 samples of supervised training per frame, applied in real-time as samples arrive. Additionally, after DUID is determined, the remaining NID parity bits are predictable (BCH encoding is deterministic), potentially providing up to 24 more dibits of training.

**Alternatives considered**:
- Full retroactive sync training: Higher impact (24 known symbols) but requires sample buffering and re-equalization — too complex for initial implementation
- Sync-only (no NID): Only 24 symbols every ~110 symbols — not enough training density for fast-changing channels
- Decision: Start with prospective NID training. If insufficient, add retroactive sync training in a follow-up.

## R3: Decision-Directed Convergence Detection

**Decision**: Exponential moving average of modulus variance (|y|² - 1)² with threshold 0.1.

**Rationale**: CMA drives |y|² toward 1.0 (constant modulus). When converged, all symbols have |y|² ≈ 1.0 and the variance of |y|² is small. An EMA with α=0.01 (100-symbol window) smooths symbol-level noise. When EMA < 0.1, the equalizer is converged. This is computationally cheap (one multiply-add per symbol) and directly measures what CMA optimizes.

**Alternatives considered**:
- MSE to constellation: More direct but requires hard decisions, which are unreliable before convergence — chicken-and-egg
- Fixed timer (switch after N symbols): Too rigid — convergence time varies with channel conditions
- Tap change rate: Complex to compute, indirect measure

## R4: Pi/4 DQPSK Constellation Points for DD Mode

**Decision**: Use 4-point constellation at phases π/4, 3π/4, -π/4, -3π/4 (unit magnitude).

**Rationale**: These are the standard P25 CQPSK differential constellation points already defined in `Dibit.java`. The DD update uses the nearest constellation point as the reference signal d_hat. Nearest-point decision is a simple quadrant test on the equalized symbol's I/Q components.

**Notes**: P25 uses differential encoding, so the constellation rotates by π/4 between symbols. The DD equalizer operates on the post-differential-decode symbols, where the constellation is fixed. This means DD training happens after differential demod in the demodulator, feeding corrections back to the pre-demod equalizer.

## R5: LMS Step Size Selection

**Decision**: LMS mu_train = 0.05 for training mode, DD mu = 0.01, CMA mu = 0.001 (tracking).

**Rationale**: Training-assisted LMS with known reference can use aggressive step sizes because the error gradient is exact (not estimated). Literature suggests mu = 0.05-0.1 for LMS with known training in narrowband channels. For DD mode, mu = 0.01 provides a balance between tracking speed and decision error amplification. CMA tracking at 0.001 is already proven (spec 018).

**Alternatives considered**:
- Single mu for all modes: Rejected — each mode has fundamentally different error quality (known > decided > blind)
- Adaptive mu (NLMS): Good future enhancement but adds complexity for initial implementation
