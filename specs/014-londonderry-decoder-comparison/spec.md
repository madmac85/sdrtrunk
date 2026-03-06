# Feature Specification: Londonderry FD Decoder Comparison

**Feature Branch**: `014-londonderry-decoder-comparison`
**Created**: 2026-02-18
**Status**: Complete
**Input**: User description: "I added another sample in the Londonderry FD folder. Determine the optimal decoder for this channel."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run 3-Way Decoder Comparison (Priority: P1)

A user has a baseband recording from the Londonderry FD channel (156.1125 MHz, P25 Phase 1, NAC 293, FCC callsign KQO260) and wants to determine which P25 Phase 1 decoder — C4FM, LSM, or LSM v2 — produces the best decode quality for this channel.

**Why this priority**: This is the core deliverable. Without the comparison data, no decoder recommendation can be made.

**Independent Test**: Can be fully tested by running the 3-way comparison against the baseband sample and comparing LDU counts, valid message rates, sync metrics, and bit error rates across all three decoders.

**Acceptance Scenarios**:

1. **Given** the Londonderry FD baseband file at `_SAMPLES/Londonderry FD/20260218_080031_156112500_Municipalities_Londonderry_Londonderry-FD_14_baseband.wav`, **When** the 3-way comparison test is run with NAC 293, **Then** each decoder reports LDU count, valid message count, sync blocked count, NID success rate, and bit error rate.
2. **Given** comparison results from all three decoders, **When** the scoring algorithm evaluates the metrics, **Then** a clear decoder recommendation is produced with supporting evidence.

---

### User Story 2 - Establish Gold Standard Baseline (Priority: P2)

After identifying the best decoder, the user wants to establish a gold standard baseline for the Londonderry FD channel — a reference LDU count and regression threshold — so future code changes can be validated against this channel.

**Why this priority**: A gold standard enables ongoing regression testing, but the comparison must be completed first.

**Independent Test**: Can be tested by recording the winning decoder's LDU count and verifying that re-running the comparison produces the same result.

**Acceptance Scenarios**:

1. **Given** the winning decoder's metrics, **When** the comparison is re-run, **Then** the LDU count is reproducible (identical across runs).
2. **Given** the gold standard metrics are documented, **When** a future code change is made, **Then** the Londonderry FD comparison can be run as a regression check.

---

### User Story 3 - Verify Modulation Type (Priority: P2)

The channel is currently configured as C4FM in the playlist, but the FCC emission designator is not available to confirm whether this is a C4FM (single-site) or CQPSK/LSM (simulcast) channel. The comparison results will empirically determine the correct modulation.

**Why this priority**: Incorrect modulation configuration degrades decode quality. The comparison inherently answers this question by showing which decoder performs best.

**Independent Test**: Can be tested by comparing the relative performance of C4FM vs LSM/LSM v2 — a large C4FM advantage confirms C4FM modulation; a large LSM/LSM v2 advantage suggests CQPSK.

**Acceptance Scenarios**:

1. **Given** a P25 channel with unknown modulation type, **When** C4FM significantly outperforms LSM and LSM v2 in LDU count, **Then** the channel is confirmed as C4FM.
2. **Given** a P25 channel with unknown modulation type, **When** LSM or LSM v2 significantly outperforms C4FM, **Then** the channel is likely CQPSK/simulcast and the playlist should be updated.

### Edge Cases

- What happens if the baseband file contains no valid P25 transmissions (e.g., the channel was idle during the recording)?
- How does the comparison handle if one decoder produces zero LDUs while others decode successfully?
- What if two decoders produce statistically identical results (within 1-2% LDU count)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The comparison MUST process the Londonderry FD baseband file through all three P25 Phase 1 decoders (C4FM, LSM, LSM v2)
- **FR-002**: The comparison MUST use NAC 293 (the Londonderry FD Network Access Code) for all decoders
- **FR-003**: The comparison MUST report per-decoder metrics: LDU count, valid message count, total messages, sync blocked count, NID success rate, and bit error count
- **FR-004**: The comparison MUST produce a decoder recommendation based on the scoring algorithm (LDU count 3pts, valid messages 2pts, lowest sync losses 1pt, lowest BER 1pt)
- **FR-005**: The comparison MUST report LDU gap analysis (gaps >500ms) for each decoder to assess temporal decode continuity
- **FR-006**: The gold standard baseline MUST be documented in the results file with the winning decoder, LDU count, and sample file reference

### Key Entities

- **Baseband Recording**: 1.3 GB WAV file (~12.8 minutes of 50 kHz stereo I/Q samples) from Londonderry FD 156.1125 MHz
- **Channel**: Londonderry FD — P25 Phase 1, NAC 293, FCC KQO260, 156.1125 MHz VHF
- **Decoders**: C4FM (conventional 4-level FM), LSM (Linear Simulcast Modulation), LSM v2 (improved LSM with cold-start resets and fade recovery)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All three decoders produce non-zero LDU counts from the baseband recording, confirming the file contains valid P25 transmissions
- **SC-002**: The comparison produces a clear decoder recommendation with at least a 10% LDU count advantage for the winner, or documents a tie if results are within 5%
- **SC-003**: The gold standard baseline is documented with a specific LDU count that is reproducible across repeated runs
- **SC-004**: The results identify the correct modulation type for the channel based on empirical decoder performance

## Assumptions

- The existing 3-way comparison test infrastructure (DerryDecoderComparisonTest, Gradle tasks) can be reused for the Londonderry FD sample with NAC parameterization
- The baseband file at `_SAMPLES/Londonderry FD/` contains at least some P25 voice traffic (LDUs) to compare
- NAC 293 is correct per RadioReference; if decode fails with NAC 293, auto-track (NAC 0) will be tried as a fallback
- The channel is a single-site conventional repeater based on the FCC license (Town of Londonderry, single callsign KQO260)
