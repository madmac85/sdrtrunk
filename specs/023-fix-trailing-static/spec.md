# Feature Specification: Fix Trailing Digital Static on C4FM Transmissions

**Feature Branch**: `023-fix-trailing-static`
**Created**: 2026-03-14
**Status**: Draft
**Input**: User description: "Fix trailing digital static at end of C4FM transmissions. After the holdover fix (DUID correction limit), 1-3 fake LDUs still get decoded before the limit kicks in, producing brief digital static (~250ms) at the end of transmissions."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Clean Transmission Endings (Priority: P1)

As a radio monitor listening to C4FM conventional channels, when a transmission ends I hear a brief burst of digital static (~250ms) before silence. This is distracting and degrades the listening experience, especially on busy channels where it occurs after every transmission.

**Why this priority**: This is the primary user-facing defect. Every C4FM transmission ends with an audible artifact instead of clean silence. On channels with frequent short transmissions (fire dispatch, law enforcement), the cumulative effect is significant.

**Independent Test**: Decode a C4FM baseband recording containing multiple transmissions. Compare the last 250ms of each decoded audio segment to silence. Can be verified by waveform analysis (RMS amplitude) and by listening.

**Acceptance Scenarios**:

1. **Given** a C4FM channel with active voice transmissions, **When** a transmission ends naturally (TDU received), **Then** the decoded audio transitions to silence without audible digital static artifacts.
2. **Given** a C4FM channel where the TDU is missed and the DUID correction limit terminates the voice frame cycle, **When** the correction limit is reached, **Then** no more than 1 IMBE frame (~20ms) of codec-decoded noise is audible before silence.
3. **Given** a C4FM channel with back-to-back transmissions separated by brief silence, **When** one transmission ends and another begins, **Then** the gap between them contains no digital static artifacts.

---

### User Story 2 - No Regression on Voice Quality (Priority: P1)

As a radio monitor, the fix for trailing static must not clip or suppress legitimate voice audio. Real speech at the end of a transmission (e.g., final words before key-up release) must be preserved.

**Why this priority**: Equal to P1 because any fix that suppresses trailing static by aggressively cutting audio could also clip the final words of real transmissions, which is worse than the static itself.

**Independent Test**: Run STT (speech-to-text) regression on known C4FM sample files. Word count must not decrease compared to the current baseline.

**Acceptance Scenarios**:

1. **Given** a C4FM channel with voice transmissions, **When** the trailing static fix is active, **Then** STT word count on regression samples is within 5% of the baseline (no fix).
2. **Given** a transmission that ends with speech right up to the TDU, **When** the fix is applied, **Then** the final words are fully audible and not clipped or attenuated.

---

### User Story 3 - CQPSK/LSM Channels Unaffected (Priority: P2)

As a radio monitor using CQPSK/LSM (simulcast) channels, any trailing static fix must not affect decode quality on these channels, which have different signal characteristics and where mid-call TDU mis-decodes are common.

**Why this priority**: CQPSK channels (ROC W, ROC E) are currently working well. The fix must be modulation-aware to avoid regression.

**Independent Test**: Run full decode regression on ROC W samples with the fix active. All metrics (LDU count, quality score, STT words) must match baseline.

**Acceptance Scenarios**:

1. **Given** a CQPSK_V2 channel (ROC W), **When** the trailing static fix is active, **Then** LDU count, audio quality score, and STT word count are identical to baseline (within measurement variance).

---

### Edge Cases

- What happens when a transmission fades gradually (weak signal) rather than ending abruptly? The fix must not prematurely suppress audio during legitimate weak-signal voice.
- What happens on encrypted channels where IMBE frame content differs? The fix should not interfere with encryption detection logic.
- What happens when the DUID correction limit is set to 1 (most aggressive)? Does it cause genuine mid-call LDUs to be misidentified as noise on channels with poor signal?
- What happens with very short transmissions (single LDU pair)? The fix must not suppress the entire transmission.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST suppress or eliminate audible digital static artifacts at the end of C4FM voice transmissions caused by IMBE codec decoding noise-derived fake LDU frames.
- **FR-002**: System MUST preserve all legitimate voice audio, including speech at the end of transmissions immediately before key-up release.
- **FR-003**: System MUST apply the trailing static fix only to C4FM modulation channels. CQPSK/LSM channels MUST be unaffected.
- **FR-004**: System MUST produce a smooth audio transition (fade or clean cut) at transmission end rather than an abrupt artifact-to-silence boundary.
- **FR-005**: System MUST handle the case where the DUID correction limit breaks the fake LDU cycle (1-3 corrected LDUs before TDU accepted) by suppressing the audio output of those corrected frames.
- **FR-006**: System MUST NOT introduce audible artifacts (clicks, pops, or abrupt amplitude changes) when transitioning from voice to silence.

### Key Entities

- **DUID-Corrected LDU**: An LDU frame whose DUID was changed from TDU to LDU by the context-aware correction logic. These frames contain noise, not voice, and are the source of the trailing static.
- **IMBE Noise Signature**: The characteristic output of the IMBE codec when fed noise input: low amplitude (RMS ~0.004), spectral energy concentrated around 2800-2900Hz, low frame-to-frame correlation.
- **Consecutive Correction Counter**: The existing counter in P25P1MessageFramer that tracks how many sequential DUID corrections have occurred. Frames produced during this count are suspect.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Trailing static duration after C4FM transmission end is reduced from ~250ms to less than 40ms (2 IMBE frames or fewer).
- **SC-002**: STT word count on C4FM regression samples (Derry FD, LFD, Hudson FD) does not decrease by more than 2% compared to the current baseline.
- **SC-003**: ROC W (CQPSK_V2) decode metrics (LDU count, quality score) are identical to baseline with the fix active.
- **SC-004**: No audible clicks, pops, or abrupt transitions at transmission end boundaries when listening to decoded audio.
- **SC-005**: Waveform analysis of the last 250ms of decoded transmissions shows RMS amplitude below 0.002 (current trailing static measures ~0.004 RMS).

## Assumptions

- The trailing static is exclusively caused by DUID-corrected fake LDU frames (noise decoded by IMBE codec), not by legitimate weak-signal voice.
- The IMBE noise signature (low RMS, ~2800Hz spectral peak) is sufficiently distinct from real speech to enable reliable detection without false positives.
- The existing DUID correction counter in P25P1MessageFramer can be used to flag suspect frames to downstream audio processing.
- The fix will operate within the existing audio processing pipeline (P25P1AudioModule) without requiring changes to the decode pipeline or message framing logic.
- Sample file `_SAMPLES/rocnh-7-1773539883.mp3` (last 0.25s) is representative of the trailing static artifact across C4FM channels.
