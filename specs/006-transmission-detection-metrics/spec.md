# Feature Specification: Transmission Detection Metrics

**Feature Branch**: `006-transmission-detection-metrics`
**Created**: 2026-01-31
**Status**: Complete
**Input**: User description: "Continue with optimization. We need detailed metrics on the number of expected transmissions in a file, based on signal strength, vs the decode performance. This may require creating an external or separate file processor that determines if a signal should be present from the raw IQ samples. Then provide metrics on the number of transmissions decoded for LSM and LSM v2"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Signal-Based Transmission Detection (Priority: P1)

As a decoder developer, I want to detect expected transmissions from raw I/Q samples based on signal energy levels, independent of any decoder, so that I have a ground-truth baseline for measuring decoder effectiveness.

**Why this priority**: Without reliable transmission detection, we cannot measure decode performance. This is the foundational capability that enables all other metrics.

**Independent Test**: Can be tested by running the signal detector on a baseband file and verifying it identifies periods where signal energy indicates RF carrier presence, matching expected PTT (push-to-talk) transmission patterns.

**Acceptance Scenarios**:

1. **Given** a baseband WAV file with P25 transmissions, **When** the signal detector processes the file, **Then** it identifies all periods where signal energy exceeds the noise floor threshold
2. **Given** a baseband file with multiple transmissions separated by silence, **When** the detector completes, **Then** each distinct transmission is identified with start/end timestamps
3. **Given** a baseband file with continuous signal (no gaps), **When** the detector completes, **Then** it reports a single transmission spanning the entire recording

---

### User Story 2 - Decoder Performance Comparison (Priority: P1)

As a decoder developer, I want to compare LSM and LSM v2 decoder performance against the expected transmissions, so that I can quantify which decoder recovers more transmissions and by how much.

**Why this priority**: This directly addresses the optimization goal - measuring decoder effectiveness against a ground truth.

**Independent Test**: Can be tested by running both decoders on a baseband file and comparing detected messages against expected transmission windows.

**Acceptance Scenarios**:

1. **Given** expected transmission windows from the signal detector, **When** both decoders process the same file, **Then** the system reports which transmissions each decoder successfully decoded
2. **Given** a transmission window with known duration, **When** a decoder produces LDUs within that window, **Then** the system calculates a decode rate (actual LDUs / expected LDUs)
3. **Given** multiple sample files, **When** analysis completes, **Then** aggregate statistics show total expected vs decoded for each decoder

---

### User Story 3 - Detection Quality Metrics Report (Priority: P2)

As a decoder developer, I want a comprehensive report showing signal detection metrics alongside decode performance, so that I can correlate signal quality with decode success.

**Why this priority**: Understanding the relationship between signal quality and decode success helps identify optimization opportunities.

**Independent Test**: Can be tested by running analysis on sample files and verifying the report contains all required metrics with correct calculations.

**Acceptance Scenarios**:

1. **Given** analysis results from multiple files, **When** the report is generated, **Then** it shows per-file and aggregate statistics for expected vs decoded transmissions
2. **Given** transmissions with varying signal strength, **When** the report is generated, **Then** it correlates decode success rate with signal quality metrics
3. **Given** LSM and v2 results, **When** the report is generated, **Then** it highlights transmissions where v2 succeeded but LSM failed (and vice versa)

---

### User Story 4 - Standalone Signal Detector Tool (Priority: P3)

As a decoder developer, I want a standalone command-line tool to analyze signal presence in baseband files without running decoders, so that I can quickly assess recording quality.

**Why this priority**: Useful for triaging recordings and understanding signal characteristics before detailed analysis.

**Independent Test**: Can be tested by running the standalone tool on a file and verifying output shows transmission count and timing.

**Acceptance Scenarios**:

1. **Given** a baseband WAV file path, **When** the tool is executed, **Then** it outputs transmission count, total signal duration, and timing summary
2. **Given** the tool completes, **When** verbose mode is enabled, **Then** each transmission is listed with start/end times and signal strength metrics

---

### Edge Cases

- What happens when signal energy is borderline (barely above threshold)?
  - System uses hysteresis to prevent rapid on/off toggling
- How does system handle files with no detectable transmissions?
  - Report zero expected transmissions with appropriate messaging
- What happens when the entire file is continuous signal (trunk channel)?
  - Detect as single long transmission; expected LDU count based on duration
- How to handle recordings that start/end mid-transmission?
  - Mark incomplete transmissions; adjust expected LDU calculation accordingly
- What happens with corrupted or invalid WAV files?
  - Report clear error message without crashing

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect signal presence from raw I/Q samples using energy-based analysis
- **FR-002**: System MUST identify transmission boundaries (start/end timestamps) based on signal energy transitions
- **FR-003**: System MUST calculate expected LDU count for each detected transmission based on duration (1 LDU = 180ms)
- **FR-004**: System MUST track signal quality metrics per transmission (peak energy, average energy, duration)
- **FR-005**: System MUST compare expected transmissions against actual decoded LDUs for both LSM and LSM v2 decoders
- **FR-006**: System MUST calculate decode rate for each transmission (decoded LDUs / expected LDUs)
- **FR-007**: System MUST generate aggregate statistics across multiple files showing total expected vs decoded
- **FR-008**: System MUST identify "missed" transmissions (expected but no LDUs decoded)
- **FR-009**: System MUST identify "v2-only" transmissions (v2 decoded, LSM did not)
- **FR-010**: System MUST use configurable thresholds for signal detection sensitivity

### Key Entities

- **SignalDetection**: Represents a period of detected signal presence with start/end timestamps, peak/average energy, and completeness flag
- **ExpectedTransmission**: A signal detection period with calculated expected LDU count based on duration
- **TransmissionDecodeResult**: Combines expected transmission with actual decode results from both decoders (LDU counts, decode rate)
- **DetectionMetrics**: Aggregate statistics across all transmissions (total expected, total decoded, decode rates, missed counts)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Signal detector identifies 95%+ of actual transmissions in test recordings (validated against manual inspection)
- **SC-002**: Expected LDU calculations are within 10% of theoretical maximum based on transmission duration
- **SC-003**: Decode rate calculations match manual verification on sample transmissions
- **SC-004**: Analysis completes for an 8-file test suite in under 5 minutes total
- **SC-005**: Report clearly shows which decoder (LSM or v2) performs better per transmission and overall
- **SC-006**: Missed transmission detection correctly identifies 100% of transmissions where neither decoder produced LDUs
- **SC-007**: Report enables identification of optimization opportunities (e.g., signal quality ranges where v2 excels)

## Assumptions

- Baseband recordings are in WAV format with I/Q samples
- Sample rate is consistent throughout each file
- P25 Phase 1 protocol with 4800 symbol rate and 180ms LDU timing
- Signal energy threshold approach (matching existing TransmissionMapper) is sufficient for detection
- Test files contain conventional (PTT) transmissions with distinct on/off boundaries
- Existing TransmissionMapper class provides a foundation that can be extended or reused

## Out of Scope

- Real-time transmission detection during live decoding
- Support for other protocols beyond P25 Phase 1
- Audio quality assessment (PESQ/POLQA metrics)
- Automatic threshold calibration based on noise floor measurement
- GUI visualization of signal detection results
