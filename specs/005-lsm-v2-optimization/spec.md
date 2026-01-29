# Feature Specification: P25 LSM v2 Decoder Optimization - Missed Transmission Recovery

**Feature Branch**: `005-lsm-v2-optimization`
**Created**: 2026-01-29
**Status**: Complete
**Input**: User description: "Using the new frameworking for transmission scoring, perform further optimizations. Specifically, attempt to decode transmissions that were missed. Also pay special attention to transmissions that were partially decoded and missing either a START signal or TERMination signal since we know those signals should be there. Perform further research on the P25 convention PTT spec as needed to help. Consider a wide array of possibilities and try several draft implementations and iterations. A final report should be compiled discussing the research and optimization outcomes. An executive summary should be included to show the deltas of LDU, Start Signals, and TDUs compared to LSM, LSM v2 baseline, and the new LSM v2 optimized that you will be creating."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recover Missed Transmissions (Priority: P1)

As a decoder developer, I want the LSM v2 decoder to successfully decode transmissions that were previously missed entirely (0% decode rate) so that more audio content is captured from marginal signal conditions.

**Why this priority**: Missed transmissions represent complete loss of audio content. Recovering even partial decode from previously-missed transmissions provides the most significant improvement in total audio captured.

**Independent Test**: Run transmission scoring on sample files, identify transmissions with 0% v2 decode rate, implement optimizations, and verify improved decode rates on previously-missed transmissions.

**Acceptance Scenarios**:

1. **Given** a baseband recording with transmissions that scored 0% decode rate, **When** the optimized decoder processes the recording, **Then** at least 50% of previously-missed transmissions achieve >10% decode rate
2. **Given** a transmission with detectable signal energy but no decoded frames, **When** the optimized decoder processes it, **Then** the decoder attempts multiple sync acquisition strategies before giving up
3. **Given** a weak signal transmission, **When** conventional sync detection fails, **Then** the decoder applies enhanced error correction to recover sync patterns

---

### User Story 2 - Recover Missing HDU (Header Data Unit) at Transmission Start (Priority: P1)

As a decoder developer, I want the LSM v2 decoder to successfully decode the HDU at the beginning of transmissions so that transmission framing is complete and call setup information is captured.

**Why this priority**: The HDU contains critical call information (encryption parameters, talk group, etc.) and establishes proper framing for the transmission. Missing HDUs indicate cold-start acquisition issues that affect overall decode quality.

**Independent Test**: Run transmission scoring to identify transmissions missing HDU, implement optimizations targeting transmission start, verify HDU detection rate improves.

**Acceptance Scenarios**:

1. **Given** a transmission with detected signal energy at start but no decoded HDU, **When** the optimized decoder processes it, **Then** HDU detection rate improves by at least 20% compared to baseline v2
2. **Given** a cold-start scenario where carrier turns on, **When** the decoder detects energy rise, **Then** sync acquisition begins within 10ms of signal presence
3. **Given** a corrupted or partial HDU, **When** standard decoding fails, **Then** the decoder attempts error correction recovery of HDU content

---

### User Story 3 - Recover Missing TDU (Terminator Data Unit) at Transmission End (Priority: P1)

As a decoder developer, I want the LSM v2 decoder to successfully decode the TDU/TDULC at the end of transmissions so that transmission framing is complete and proper call teardown occurs.

**Why this priority**: Missing TDUs indicate issues at transmission end that may cause audio to be truncated or improperly segmented. Proper TDU detection ensures clean audio boundaries.

**Independent Test**: Run transmission scoring to identify transmissions missing TDU, analyze why TDU was missed, implement optimizations, verify TDU detection rate improves.

**Acceptance Scenarios**:

1. **Given** a transmission with LDU frames decoded but no TDU detected, **When** the optimized decoder processes it, **Then** TDU detection rate improves by at least 15% compared to baseline v2
2. **Given** signal fade at transmission end, **When** the carrier drops, **Then** the decoder attempts to decode any pending frames before declaring transmission end
3. **Given** a weak TDU signal, **When** standard decoding fails, **Then** the decoder applies enhanced error correction to recover terminator

---

### User Story 4 - Research and Document P25 Conventional PTT Behavior (Priority: P2)

As a decoder developer, I want comprehensive research on P25 conventional (non-trunked) PTT channel behavior documented so that optimization strategies are grounded in protocol understanding.

**Why this priority**: Understanding the P25 spec for conventional channels informs what optimizations are possible. This research enables targeted improvements rather than trial-and-error.

**Independent Test**: Produce a research document covering P25 conventional PTT timing, frame structure, and cold-start behavior that can guide implementation decisions.

**Acceptance Scenarios**:

1. **Given** the P25 TIA-102 specification, **When** analyzed for conventional PTT behavior, **Then** key timing parameters and frame structure are documented
2. **Given** observed behavior in sample recordings, **When** compared to specification, **Then** deviations or implementation-specific behaviors are identified
3. **Given** research findings, **When** optimization strategies are proposed, **Then** each strategy is tied to specific protocol understanding

---

### User Story 5 - Generate Comparative Performance Report (Priority: P1)

As a project stakeholder, I want a comprehensive report comparing decoder performance across LSM, LSM v2 baseline, and LSM v2 optimized so that improvement can be quantified and decisions informed.

**Why this priority**: Quantitative comparison is essential to validate that optimizations provide real improvement without regressions. The executive summary enables quick assessment of results.

**Independent Test**: Run all decoders on the same sample files, collect metrics, generate report with executive summary showing deltas.

**Acceptance Scenarios**:

1. **Given** multiple decoder versions (LSM, v2 baseline, v2 optimized), **When** run on the same sample files, **Then** LDU count, HDU detection rate, and TDU detection rate are collected for each
2. **Given** collected metrics, **When** the report is generated, **Then** it includes executive summary with delta tables showing improvement/regression for each metric
3. **Given** optimization iterations, **When** multiple approaches are tested, **Then** the report documents each approach tried, its outcome, and rationale for final decisions

---

### Edge Cases

- What happens when signal is present but below decode threshold for entire transmission?
- How does the decoder handle interleaved valid/invalid frames within a single transmission?
- What happens when HDU is corrupted beyond recovery but subsequent LDUs are valid?
- How does the decoder behave when TDU is transmitted but signal fades before completion?
- What happens when transmission boundaries are ambiguous (brief gaps in otherwise continuous signal)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST implement at least 3 different optimization strategies for missed transmission recovery
- **FR-002**: System MUST track and report HDU detection rate per transmission
- **FR-003**: System MUST track and report TDU/TDULC detection rate per transmission
- **FR-004**: System MUST maintain backwards compatibility with existing LSM v2 decoder interface
- **FR-005**: System MUST not regress total LDU count compared to baseline v2 decoder
- **FR-006**: System MUST document each optimization strategy attempted with rationale and outcome
- **FR-007**: System MUST produce a final report with executive summary comparing LSM, v2 baseline, and v2 optimized
- **FR-008**: System MUST include delta metrics for: Total LDUs, HDU detection rate, TDU detection rate
- **FR-009**: System MUST test optimizations on all 8 sample files in _SAMPLES directory
- **FR-010**: System SHOULD research P25 TIA-102 conventional channel specifications to inform optimizations
- **FR-011**: System MUST preserve existing decoder parameters (NAC configuration, sample rate handling)
- **FR-012**: System MUST log diagnostic information about optimization decisions for debugging

### Key Entities

- **Transmission**: A detected period of signal activity with boundaries (start/end time, expected frames)
- **TransmissionScore**: Decode quality metrics for a transmission (LDU count, HDU presence, TDU presence)
- **DecoderStats**: Aggregate statistics from a decoder run (total messages, LDUs, HDUs, TDUs, errors)
- **OptimizationIteration**: A specific parameter/algorithm change with before/after metrics

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 30% of previously-missed transmissions (0% decode rate in baseline v2) achieve >10% decode rate in optimized v2
- **SC-002**: HDU detection rate improves by at least 15% compared to baseline v2 across all sample files
- **SC-003**: TDU detection rate improves by at least 10% compared to baseline v2 across all sample files
- **SC-004**: Total LDU count does not regress (must be >= baseline v2 total)
- **SC-005**: Executive summary report clearly shows delta metrics in tabular format
- **SC-006**: At least 3 distinct optimization strategies are documented with outcomes
- **SC-007**: Research findings on P25 conventional PTT behavior are documented
- **SC-008**: All optimizations are validated on the full set of 8 sample files (not just cherry-picked examples)
