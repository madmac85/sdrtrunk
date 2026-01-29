# Feature Specification: Transmission Detection and Decode Scoring System

**Feature Branch**: `004-transmission-scoring`
**Created**: 2026-01-28
**Status**: Complete
**Input**: User description: "We really need to determine WHEN a transmission is in progress from the raw IQ samples. We then need to determine what percent of that transmission we are able to decode with the original LSM and LSM v2 decoder. This is a critical step to actually measure how much we are missing so we can continue to improve and identify the exact cases where we are having an issue. Then we can iterate on trying to decode/correct those small sections before doing a full regression. This will allow faster iteration."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Map Transmissions in Baseband Recording (Priority: P1)

As a decoder developer, I want to automatically detect and map all transmissions in a baseband recording using signal energy analysis so that I have a ground-truth reference of when transmissions occurred, independent of decode success.

**Why this priority**: This is the foundational capability. Without knowing WHEN transmissions occur, we cannot measure decode completeness or identify problematic segments.

**Independent Test**: Can be tested by running the mapper on a baseband file and producing a transmission map that lists each transmission's start time, end time, and duration. The output can be visually verified against the recording.

**Acceptance Scenarios**:

1. **Given** a baseband recording with multiple transmissions, **When** the transmission mapper analyzes it, **Then** it produces a list of transmission boundaries with start/end timestamps
2. **Given** a transmission map, **When** the mapper runs, **Then** each transmission entry includes: start time (ms), end time (ms), duration (ms), and peak signal energy
3. **Given** adjacent transmissions with a brief gap, **When** gap is less than 500ms, **Then** they are detected as separate transmissions (not merged)
4. **Given** a transmission boundary, **When** creating slices, **Then** a configurable buffer (default 100ms) is added before and after

---

### User Story 2 - Score Decode Completeness per Transmission (Priority: P1)

As a decoder developer, I want to score how completely each transmission was decoded so that I can quantify decoder performance and identify transmissions with poor decode rates.

**Why this priority**: Scoring is the core metric needed to compare decoder versions and identify regressions or improvements. This is essential for data-driven optimization.

**Independent Test**: Can be tested by running both decoders on a mapped recording and generating a score report showing decode percentage for each transmission.

**Acceptance Scenarios**:

1. **Given** a transmission map and decode results, **When** scoring runs, **Then** each transmission gets a completeness score (0-100%) based on LDU frames decoded vs. expected
2. **Given** a transmission of known duration, **When** expected LDU count is calculated, **Then** it equals duration divided by 180ms (LDU frame duration)
3. **Given** a transmission with valid HDU (Header), **When** scoring, **Then** the "valid start" metric is marked true
4. **Given** a transmission with valid TDU or TDULC (Terminator), **When** scoring, **Then** the "valid end" metric is marked true
5. **Given** LSM and LSM v2 decode results, **When** compared, **Then** report shows per-transmission deltas highlighting where each decoder performs better

---

### User Story 3 - Extract Transmission Slices (Priority: P2)

As a decoder developer, I want to extract individual transmissions as separate baseband files so that I can focus testing and optimization on specific problematic segments without running the full recording.

**Why this priority**: Slice extraction enables rapid iteration by allowing developers to test changes on small, targeted segments rather than full multi-minute recordings.

**Independent Test**: Can be tested by extracting a slice and verifying it contains valid signal data at the expected timestamps and produces expected decode output.

**Acceptance Scenarios**:

1. **Given** a transmission in the map, **When** extraction runs, **Then** a new WAV file is created containing just that transmission plus buffer
2. **Given** an extracted slice, **When** decoded independently, **Then** it produces the same LDU count as when decoded from the full recording
3. **Given** multiple transmissions flagged as problematic, **When** batch extraction runs, **Then** all are extracted to separate files with descriptive names

---

### User Story 4 - Generate Comparative Analysis Report (Priority: P2)

As a decoder developer, I want a summary report comparing LSM vs LSM v2 performance across all transmissions so that I can quickly assess overall improvement and identify systematic issues.

**Why this priority**: Aggregate reporting enables trend analysis and helps prioritize which transmission types or signal conditions need the most attention.

**Independent Test**: Can be tested by running analysis on a recording and verifying the report contains expected summary statistics and per-transmission details.

**Acceptance Scenarios**:

1. **Given** decode results from both decoders, **When** report generates, **Then** it includes: total transmissions, average decode %, transmissions with valid start, transmissions with valid end
2. **Given** the report, **When** viewed, **Then** transmissions are ranked by decode completeness (worst first) to highlight problem areas
3. **Given** the report, **When** a transmission shows LSM better than v2, **Then** it is flagged as a "regression candidate" for investigation

---

### Edge Cases

- What happens when signal energy never clearly drops to silence? The mapper should use a dynamic threshold based on observed peak energy to detect boundaries.
- What happens when a transmission is cut off at the recording boundary? Mark it as "incomplete" and exclude from scoring or score with caveats.
- What happens when decode errors cause zero LDUs for a transmission? Score as 0% but still include in statistics.
- What happens when the HDU is corrupted but LDUs decode? Score the LDUs but mark "valid start" as false.
- What happens when two transmissions overlap (unlikely but possible)? Treat as a single extended transmission.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect transmission boundaries from baseband I/Q samples using signal energy analysis
- **FR-002**: System MUST produce a transmission map with start time, end time, duration, and peak energy for each detected transmission
- **FR-003**: System MUST calculate expected LDU count for each transmission based on duration (duration_ms / 180ms)
- **FR-004**: System MUST count actual decoded LDUs per transmission from decoder output
- **FR-005**: System MUST score each transmission as (actual LDUs / expected LDUs) * 100%
- **FR-006**: System MUST track whether each transmission has a valid HDU (Header Data Unit) at the start
- **FR-007**: System MUST track whether each transmission has a valid TDU or TDULC (Terminator) at the end
- **FR-008**: System MUST support running both LSM and LSM v2 decoders and comparing results
- **FR-009**: System MUST extract individual transmissions to separate baseband files with configurable buffer (default 100ms before/after)
- **FR-010**: System MUST generate a summary report with aggregate statistics and per-transmission details
- **FR-011**: System MUST rank transmissions by decode completeness to highlight problem areas
- **FR-012**: System MUST identify and flag transmissions where LSM outperforms v2 as regression candidates

### Key Entities

- **Transmission**: A detected period of RF activity with start time, end time, duration, and signal energy metrics
- **Transmission Map**: A list of all transmissions detected in a baseband recording with their boundaries
- **Transmission Score**: Metrics for a single transmission including decode %, valid start, valid end, LDU counts
- **Comparative Report**: Aggregated analysis comparing decoder performance across all transmissions

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Transmission detection accuracy is 95% or better (verified manually on sample recordings)
- **SC-002**: Transmission boundary timestamps are accurate within 50ms of actual signal edges
- **SC-003**: Expected LDU count calculation matches actual frame boundaries within 1 frame
- **SC-004**: Report generation completes within 30 seconds for recordings up to 10 minutes
- **SC-005**: Extracted transmission slices produce identical decode results to full-file processing
- **SC-006**: Report clearly identifies top 10 worst-performing transmissions for each recording
- **SC-007**: Regression candidates (v2 worse than LSM) are automatically flagged with 100% recall

## Assumptions

- Signal energy detection algorithms already exist in P25P1DecoderLSMv2 and can be reused or adapted
- Transmission boundaries in P25 conventional are marked by signal energy transitions (carrier on/off)
- LDU frame duration is fixed at 180ms per the P25 specification
- All voice transmissions should start with HDU and end with TDU/TDULC under ideal conditions
- Existing LSMv2ComparisonTest infrastructure can be extended for this functionality
- Test baseband recordings are available in the _SAMPLES directory

## Out of Scope

- Real-time transmission detection during live decoding
- UI for viewing transmission maps or reports (command-line output only)
- Support for P25 Phase 2 or other protocols
- Automatic optimization based on scoring results (this is a measurement tool only)
- Audio quality scoring beyond LDU count metrics
