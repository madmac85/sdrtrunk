# Feature Specification: Sync Threshold Improvement for P25 LSM v2

**Feature Branch**: `009-sync-threshold-improvement`
**Created**: 2026-01-31
**Status**: Complete
**Input**: User description: "Implement sync acquisition improvements based on investigation: 1) Lower initial sync threshold for weak preambles (72% of failures), 2) Improve recovery sync for rapid fade (8%), 3) Add energy variance and preamble metrics"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Weak Preamble Recovery (Priority: P1)

As a radio decoder user, I want the decoder to successfully acquire sync on transmissions that have weak preambles (low energy in first 100ms), so that I don't miss transmissions where the signal starts weak but has adequate average energy.

**Why this priority**: 72% of sync failures are attributed to weak preambles. This is the highest-impact improvement opportunity identified in the 008 investigation.

**Independent Test**: Can be tested by re-running the SyncFailureInvestigator on the 8-file test suite and measuring reduction in WEAK_PREAMBLE categorized failures.

**Acceptance Scenarios**:

1. **Given** a transmission with weak initial energy (first 100ms below 70% of average), **When** the decoder processes the transmission, **Then** the decoder uses a lower sync threshold during the initial acquisition window
2. **Given** the current 25 sync failures from the test suite, **When** weak preamble optimization is enabled, **Then** at least 50% of WEAK_PREAMBLE failures (9 of 18) are recovered
3. **Given** a normally strong transmission (preamble energy >= 70% of average), **When** the decoder processes the transmission, **Then** decode success rate is not affected (no regression)

---

### User Story 2 - Rapid Fade Recovery (Priority: P2)

As a radio decoder user, I want the decoder to maintain sync acquisition during rapid signal fades, so that transmissions with unstable signals can still be decoded.

**Why this priority**: 8% of sync failures show rapid fade patterns (peak-to-average ratio > 5.0). This is a secondary improvement opportunity.

**Independent Test**: Can be tested by re-running the SyncFailureInvestigator and measuring reduction in RAPID_FADE categorized failures.

**Acceptance Scenarios**:

1. **Given** a transmission with high signal instability (peak-to-average > 5.0), **When** the decoder detects rapid energy drop (> 50% in 50ms), **Then** the decoder immediately activates aggressive re-sync
2. **Given** the current 2 RAPID_FADE failures from the test suite, **When** fade recovery optimization is enabled, **Then** at least 1 of the 2 is recovered
3. **Given** existing fade recovery for TDU, **When** the optimization is applied, **Then** it extends to cover sync acquisition phase, not just transmission end

---

### User Story 3 - Enhanced Signal Quality Metrics (Priority: P3)

As a decoder developer, I want the analysis tools to track energy variance and preamble metrics, so that I can better understand sync failure patterns and validate decoder improvements.

**Why this priority**: The investigation showed that average energy alone has weak correlation (+0.146) with decode success. Energy variance and preamble quality may be better predictors.

**Independent Test**: Can be tested by running MissedTransmissionAnalyzer and verifying new metrics appear in the output.

**Acceptance Scenarios**:

1. **Given** transmission analysis runs, **When** metrics are calculated, **Then** energy variance is reported alongside average energy
2. **Given** transmission analysis runs, **When** metrics are calculated, **Then** preamble energy (first 100ms) is calculated and compared to average
3. **Given** the analysis report, **When** signal quality correlation is calculated, **Then** both average energy AND energy variance correlations are reported

---

### Edge Cases

- What happens when a transmission has zero detectable preamble energy?
  - Use existing fallback sync with hard sync detector (4-bit error tolerance)
- How does system handle transmissions that fade completely and recover multiple times?
  - Each recovery window is independent; re-sync attempts after each fade detection
- What if lowering the threshold causes false sync detections on noise?
  - Only lower threshold when signal energy tracking indicates transmission present OR during initial acquisition window (time-limited)
- How to handle transmissions at recording boundaries (start time = 0)?
  - These are already detected as INCOMPLETE category; boundary recovery is already active for first 50ms

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Decoder MUST use a lower sync threshold (configurable, default 44) during the initial acquisition window (first 100ms of detected transmission start)
- **FR-002**: Decoder MUST implement adaptive threshold that starts at the lower initial threshold and ramps up to the standard threshold over the acquisition window
- **FR-003**: Decoder MUST activate aggressive re-sync when signal energy drops more than 50% within 50ms during sync acquisition
- **FR-004**: Decoder MUST track preamble energy separately from average energy for each transmission
- **FR-005**: Analysis tools MUST calculate and report energy variance for each transmission
- **FR-006**: Analysis tools MUST calculate and report preamble energy ratio (preamble/average) for each transmission
- **FR-007**: Analysis tools MUST include energy variance in correlation analysis with decode success
- **FR-008**: Decoder improvements MUST NOT cause regression in existing successfully-decoded transmissions
- **FR-009**: Decoder MUST maintain counters for initial acquisition sync detections (separate from existing fallback/recovery counters)
- **FR-010**: System MUST provide configurable threshold values to enable tuning without code changes

### Key Entities

- **InitialAcquisitionWindow**: The first 100ms (approximately 2400 symbols at 4800 symbols/sec) of a newly detected transmission where lower sync thresholds apply
- **PreambleEnergy**: Average signal energy measured during the initial acquisition window
- **EnergyVariance**: Standard deviation of energy samples over the transmission duration
- **SyncThresholdLevel**: Enumeration of threshold tiers (INITIAL=44, FALLBACK=52, STANDARD=60)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sync failure count on 8-file test suite decreases from 25 to 16 or fewer (36% reduction)
- **SC-002**: WEAK_PREAMBLE failures decrease by at least 50% (from 18 to 9 or fewer)
- **SC-003**: RAPID_FADE failures decrease by at least 50% (from 2 to 1 or fewer)
- **SC-004**: No regression in transmissions that currently decode successfully (174 successful transmissions maintained)
- **SC-005**: Analysis tools report energy variance and preamble energy metrics in output
- **SC-006**: Energy variance shows measurable correlation (positive or negative, |r| > 0.15) with decode success
- **SC-007**: Diagnostic output distinguishes initial acquisition syncs from standard syncs

## Assumptions

- The 25 sync failure transmissions from 008 analysis are representative of typical sync acquisition issues
- Lowering the initial threshold to 44 (from 52) provides adequate margin for weak preambles without excessive false positives
- 100ms (2400 symbols) is an appropriate acquisition window based on P25 timing (one LDU is 180ms)
- The existing signal energy tracking (`isSignalPresent()`, `mInSilence`) can be used to gate threshold adaptation
- Existing LSM v2 decoder architecture supports adding new threshold logic without major restructuring
- Test suite results from 008 investigation (25 sync failures, 174 successes) are reproducible

## Out of Scope

- Changes to the original LSM decoder (focus is on LSM v2 only)
- Hardware-level signal quality improvements
- Real-time adaptive threshold learning based on historical performance
- Changes to hard sync detector bit error tolerance
- GUI or user interface changes for threshold configuration
- Timing drift or noise interference improvements (identified as LOW priority in investigation)
