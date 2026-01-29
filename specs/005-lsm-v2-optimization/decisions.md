# Implementation Decisions Log

## 2026-01-29 - Baseline Measurement Approach

**Context**: Need to establish baseline HDU/TDU detection rates before implementing optimizations.

**Decision**: Extend existing TransmissionScorer to calculate HDU/TDU detection rates as percentage of transmissions with detected HDU/TDU.

**Rationale**: The scoring infrastructure already tracks HDU/TDU presence per transmission. Calculating rates is a simple aggregation.

**Alternatives Considered**:
- Create separate HDU/TDU-specific test - rejected as duplicates existing infrastructure

---

## 2026-01-29 - Adaptive Sync Threshold Values

**Context**: Need to choose fallback sync threshold value for Strategy 1.

**Decision**: Use 45 as fallback threshold (vs 60 primary).

**Rationale**:
- 60 is the current threshold that works well for normal signal strength
- Lowering to 45 is a 25% reduction which should catch marginal sync patterns
- Going lower risks false positives from noise
- Will tune based on baseline results if needed

**Alternatives Considered**:
- 50 (more conservative) - may not provide enough improvement
- 40 (more aggressive) - higher false positive risk

---

## 2026-01-29 - Recovery Window Duration

**Context**: Need to choose how long to keep boundary recovery and fade recovery active.

**Decision**: Use 100ms (480 symbols) for boundary recovery, 50ms detection window for fade.

**Rationale**:
- HDU is ~135ms, so 100ms window covers most of HDU assembly period
- 50ms fade detection is approximately one LDU worth of samples
- Short enough to not interfere with next transmission start

**Alternatives Considered**:
- 200ms window - too long, may overlap next transmission
- 50ms window - may not cover enough of HDU

---

## 2026-01-29 - Energy Provider Interface

**Context**: MessageFramer needs signal energy information but currently has no reference to decoder.

**Decision**: Pass `ISignalEnergyProvider` interface reference to MessageFramer via new setter method.

**Rationale**:
- Minimal coupling - framer only needs energy state, not full decoder reference
- Interface already exists (`ISignalEnergyProvider`)
- Clean separation of concerns

**Alternatives Considered**:
- Pass decoder reference directly - too much coupling
- Have decoder call framer method on energy changes - more complex state management
