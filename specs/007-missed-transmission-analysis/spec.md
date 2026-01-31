# Feature Specification: Missed Transmission Analysis

**Feature Branch**: `007-missed-transmission-analysis`
**Created**: 2026-01-31
**Status**: Draft
**Input**: User description: "Analyze the 31 missed transmissions (13.5%) to identify optimization opportunities. Investigate why some transmissions have very low decode rates despite signal presence. Consider signal quality correlation analysis for optimization targeting."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Missed Transmission Root Cause Analysis (Priority: P1)

As a decoder developer, I want to analyze transmissions where neither decoder recovered any LDUs despite signal presence, so that I can identify specific failure patterns and optimization opportunities.

**Why this priority**: The 31 missed transmissions (13.5%) represent the biggest opportunity for improvement - these are complete failures where users get no audio despite valid signal.

**Independent Test**: Can be tested by running the analyzer on sample files and verifying it extracts and categorizes all missed transmissions with diagnostic information.

**Acceptance Scenarios**:

1. **Given** a set of baseband recordings with missed transmissions, **When** the analyzer runs, **Then** it extracts each missed transmission with timing, duration, and signal metrics
2. **Given** missed transmissions identified, **When** analysis completes, **Then** each is categorized by likely failure cause (too short, weak signal, sync failure, etc.)
3. **Given** categorized failures, **When** report is generated, **Then** it shows distribution of failure types with actionable insights

---

### User Story 2 - Low Decode Rate Investigation (Priority: P1)

As a decoder developer, I want to investigate transmissions with very low decode rates (e.g., <20% of expected LDUs) despite signal presence, so that I can understand partial decode failures.

**Why this priority**: Low decode rate transmissions indicate the decoder acquired signal but lost it - understanding why helps improve sync retention.

**Independent Test**: Can be tested by filtering transmissions below a decode rate threshold and analyzing their characteristics.

**Acceptance Scenarios**:

1. **Given** transmissions with decode rate below threshold, **When** analysis runs, **Then** it identifies common characteristics (duration, signal strength, position in recording)
2. **Given** low-rate transmissions, **When** compared to high-rate transmissions, **Then** differentiating factors are highlighted
3. **Given** analysis results, **When** report generated, **Then** specific timing windows where decode failed are identified

---

### User Story 3 - Signal Quality Correlation Analysis (Priority: P2)

As a decoder developer, I want to correlate signal quality metrics (energy, SNR proxy) with decode success rates, so that I can identify signal thresholds where optimization would have the most impact.

**Why this priority**: Understanding the relationship between signal quality and decode success helps prioritize which signal conditions to optimize for.

**Independent Test**: Can be tested by generating a scatter plot or correlation table of signal metrics vs decode rates.

**Acceptance Scenarios**:

1. **Given** transmissions with varying signal levels, **When** correlation analysis runs, **Then** it shows decode rate vs signal quality relationship
2. **Given** correlation data, **When** binned by signal quality ranges, **Then** shows decode success rate per quality tier
3. **Given** analysis complete, **When** report generated, **Then** identifies "optimization sweet spot" - signal range with most improvement potential

---

### User Story 4 - Transmission Extraction for Manual Analysis (Priority: P3)

As a decoder developer, I want to extract problematic transmissions to separate files for detailed manual inspection, so that I can examine specific failure cases in detail.

**Why this priority**: Some failures require manual inspection of waveforms and symbol timing to diagnose.

**Independent Test**: Can be tested by extracting a missed transmission and verifying the extracted file contains the expected time window.

**Acceptance Scenarios**:

1. **Given** a missed transmission identified, **When** extraction requested, **Then** system creates a standalone WAV file of that transmission
2. **Given** extraction complete, **When** user opens file, **Then** it contains the transmission with configurable padding before/after
3. **Given** multiple extractions requested, **When** batch extract runs, **Then** all files are created with descriptive naming

---

### Edge Cases

- What happens when a transmission is extremely short (<180ms, less than 1 LDU)?
  - Flag as "too short for reliable decode" - not a decoder failure
- How to handle transmissions at recording boundaries (cut off at start/end)?
  - Mark as "incomplete" and analyze separately from complete transmissions
- What if signal quality metrics are uniform across all transmissions?
  - Report insufficient variance for correlation analysis
- How to handle files with no missed transmissions?
  - Report success and skip failure analysis sections

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST identify all missed transmissions (zero LDUs decoded by both decoders)
- **FR-002**: System MUST categorize missed transmissions by likely cause:
  - Too short (duration < 180ms)
  - Weak signal (below threshold)
  - Sync acquisition failure (adequate signal, no sync)
  - Unknown (requires manual inspection)
- **FR-003**: System MUST identify low decode rate transmissions (configurable threshold, default <20%)
- **FR-004**: System MUST calculate signal quality metrics per transmission:
  - Peak energy level
  - Average energy level
  - Energy variance (stability indicator)
  - Duration
- **FR-005**: System MUST correlate signal quality with decode success rate
- **FR-006**: System MUST bin transmissions by signal quality tiers and report decode rates per tier
- **FR-007**: System MUST identify the "optimization opportunity zone" - signal range where improvement is most needed
- **FR-008**: System MUST support extraction of individual transmissions to separate files
- **FR-009**: System MUST generate summary report with actionable recommendations
- **FR-010**: System MUST compare v2 vs LSM performance within each failure category

### Key Entities

- **MissedTransmission**: A transmission where both decoders recovered zero LDUs, with failure category and metrics
- **LowRateTransmission**: A transmission with decode rate below threshold, with timing of decode gaps
- **SignalQualityBin**: A range of signal quality with aggregate decode statistics
- **FailureCategory**: Classification of why a transmission failed (TOO_SHORT, WEAK_SIGNAL, SYNC_FAILURE, UNKNOWN)
- **OptimizationOpportunity**: A signal quality range where decoder improvements would yield significant gains

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 31 missed transmissions (from 8-file test suite) are categorized with failure causes
- **SC-002**: Low decode rate transmissions (<20%) are identified with specific failure timing
- **SC-003**: Signal quality correlation shows clear relationship (or documents lack thereof)
- **SC-004**: Report identifies at least one actionable optimization opportunity
- **SC-005**: Analysis completes for 8-file test suite in under 10 minutes
- **SC-006**: Extracted transmission files are playable and contain correct time windows
- **SC-007**: Report distinguishes between "impossible to decode" (too short/weak) and "optimization opportunity" failures

## Assumptions

- Existing TransmissionDetectionAnalyzer provides transmission detection and decode results
- Signal quality can be estimated from energy metrics already captured in Transmission record
- "Too short" threshold is 180ms (one LDU duration)
- "Weak signal" threshold is configurable but defaults to bottom 10% of observed energy levels
- Transmissions at recording boundaries are tracked via existing `isComplete` flag
- Extraction uses existing TransmissionExtractor infrastructure

## Out of Scope

- Real-time failure detection during live decoding
- Automatic parameter tuning based on analysis results
- Audio quality assessment of decoded transmissions
- Cross-protocol analysis (P25 Phase 2, DMR, etc.)
- Machine learning-based failure prediction
