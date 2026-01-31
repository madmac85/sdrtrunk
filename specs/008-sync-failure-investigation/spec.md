# Feature Specification: Sync Failure Investigation & Decoder Improvements

**Feature Branch**: `008-sync-failure-investigation`
**Created**: 2026-01-31
**Status**: Draft
**Input**: User description: "Implement decoder improvements based on 25 sync failure cases identified. Investigate why signal quality correlation is weak (-0.075) - decode success appears unrelated to signal strength."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sync Failure Root Cause Analysis (Priority: P1)

As a decoder developer, I want to analyze the 25 identified sync failure transmissions in detail to understand why the decoder failed to acquire sync despite adequate signal, so that I can identify specific patterns and root causes.

**Why this priority**: Understanding the root cause is essential before implementing any fixes. The 25 sync failures represent 80.6% of missed transmissions and are the primary optimization opportunity.

**Independent Test**: Can be tested by extracting and analyzing the 25 sync failure transmissions, documenting specific failure characteristics for each.

**Acceptance Scenarios**:

1. **Given** the 25 sync failure transmissions identified in 007 analysis, **When** detailed analysis runs, **Then** each transmission has documented: timing characteristics, signal profile, and hypothesized failure cause
2. **Given** sync failure transmissions extracted, **When** waveform/symbol analysis performed, **Then** specific patterns are identified (e.g., timing drift, symbol distortion, interference patterns)
3. **Given** analysis results, **When** report generated, **Then** failure causes are categorized into actionable groups

---

### User Story 2 - Signal Quality Metric Investigation (Priority: P1)

As a decoder developer, I want to understand why signal quality correlation with decode success is weak (-0.075), so that I can determine if the current energy-based metrics are appropriate or if better signal quality indicators exist.

**Why this priority**: The weak correlation suggests either the metric is inadequate or decode failures are driven by factors other than signal strength. Understanding this informs optimization strategy.

**Independent Test**: Can be tested by analyzing signal characteristics of successful vs failed transmissions beyond simple energy metrics.

**Acceptance Scenarios**:

1. **Given** successful and failed transmissions, **When** alternative signal metrics calculated (SNR estimates, symbol error rates, carrier stability), **Then** metrics with stronger correlation are identified
2. **Given** failed transmissions with high energy, **When** signal characteristics examined, **Then** specific degradation patterns are documented (multipath, interference, fading)
3. **Given** analysis complete, **When** report generated, **Then** recommendations for improved signal quality metrics are provided

---

### User Story 3 - Decoder Sync Improvement Implementation (Priority: P2)

As a decoder developer, I want to implement targeted improvements to sync acquisition based on identified failure patterns, so that more transmissions are successfully decoded.

**Why this priority**: Depends on root cause analysis (P1). Implementation should target the most common failure patterns identified.

**Independent Test**: Can be tested by re-running analysis on the 8-file test suite and measuring reduction in sync failures.

**Acceptance Scenarios**:

1. **Given** root cause analysis complete, **When** targeted improvements implemented, **Then** sync failure count is reduced
2. **Given** improvements deployed, **When** 8-file test suite re-analyzed, **Then** missed transmission count decreases from 31
3. **Given** improvements deployed, **When** regression testing performed, **Then** no existing decode capability is lost (no new regressions)

---

### User Story 4 - Enhanced Signal Quality Metrics (Priority: P3)

As a decoder developer, I want to implement improved signal quality metrics that better predict decode success, so that analysis reports provide more actionable insights.

**Why this priority**: Useful for ongoing optimization but not critical for immediate decoder improvements.

**Independent Test**: Can be tested by comparing new metric correlation with decode success vs current energy-based metrics.

**Acceptance Scenarios**:

1. **Given** improved metrics identified, **When** implemented in analysis tools, **Then** correlation with decode success is stronger than -0.075
2. **Given** new metrics available, **When** analysis reports generated, **Then** signal quality bins show clearer decode rate gradients

---

### Edge Cases

- What happens when multiple failure causes are present in one transmission?
  - Document all observed causes; primary cause is the earliest/most fundamental
- How to handle transmissions with intermittent signal (rapid fading)?
  - Analyze signal stability as separate metric from average energy
- What if sync failures are truly random (no pattern)?
  - Document as "indeterminate" and flag for manual waveform inspection
- How to handle transmissions where both decoders (LSM and v2) fail identically?
  - These suggest fundamental signal issues rather than decoder bugs

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST extract and isolate each of the 25 sync failure transmissions for detailed analysis
- **FR-002**: System MUST generate detailed signal profiles for each sync failure including: energy envelope, estimated SNR, timing stability
- **FR-003**: System MUST compare signal characteristics of sync failures vs successful decodes to identify differentiating factors
- **FR-004**: System MUST categorize sync failures by likely cause (timing drift, symbol distortion, interference, weak preamble, etc.)
- **FR-005**: System MUST calculate alternative signal quality metrics beyond simple energy average
- **FR-006**: System MUST identify which sync failure patterns are most common and actionable
- **FR-007**: System MUST track decoder improvement impact by re-running analysis after changes
- **FR-008**: System MUST ensure decoder changes don't cause regressions in currently-working transmissions
- **FR-009**: System MUST document correlation strength for each signal quality metric tested
- **FR-010**: System MUST provide recommendations ranked by expected impact

### Key Entities

- **SyncFailureCase**: A missed transmission with adequate signal, including detailed signal profile and hypothesized cause
- **SignalProfile**: Detailed characterization beyond simple energy: SNR estimate, timing stability, symbol quality indicators
- **FailureCause**: Categorization of why sync acquisition failed (TIMING_DRIFT, SYMBOL_DISTORTION, WEAK_PREAMBLE, INTERFERENCE, INDETERMINATE)
- **DecoderImprovement**: A specific change to decoder behavior with measured impact on sync failure count
- **SignalMetric**: A measurable signal characteristic with calculated correlation to decode success

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 25 sync failure transmissions are analyzed with documented failure characteristics
- **SC-002**: At least 3 alternative signal quality metrics are evaluated for correlation strength
- **SC-003**: Sync failure causes are categorized with at least 80% of cases assigned a specific cause (not "indeterminate")
- **SC-004**: Investigation identifies at least one actionable improvement opportunity
- **SC-005**: If decoder improvements implemented, sync failure count decreases by at least 20% (from 25 to ≤20)
- **SC-006**: No regressions introduced - transmissions that decoded successfully before continue to decode
- **SC-007**: Analysis report explains the weak correlation finding with specific evidence
- **SC-008**: Recommendations are prioritized by expected impact and implementation complexity

## Assumptions

- The 25 sync failure transmissions from 007 analysis are representative of typical sync acquisition issues
- Signal energy metrics captured in Transmission record (peakEnergy, avgEnergy) are accurate
- TransmissionExtractor can successfully extract sync failure transmissions for detailed analysis
- Both LSM and LSM v2 decoders failed on these transmissions, so improvements apply to v2
- Baseband recordings have sufficient fidelity for detailed signal analysis

## Out of Scope

- Improvements to LSM (original) decoder - focus is on LSM v2
- Real-time adaptive sync acquisition during live decoding
- Support for other protocols beyond P25 Phase 1
- Hardware-level signal quality improvements
- User interface for viewing analysis results
