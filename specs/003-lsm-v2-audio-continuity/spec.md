# Feature Specification: P25 LSM v2 Audio Continuity Optimization

**Feature Branch**: `003-lsm-v2-audio-continuity`
**Created**: 2026-01-28
**Status**: Complete
**Input**: User description: "Further and focused optimization of the v2 decoder is needed. In addition to improving cold start of bursty transmission and decode of short transmissions, we want to decrease false 'stops' in audio decode where errors cause a audio recording to end prematurely while the transmission is still in progress. This could be addressed by adding up to 0.25sec (configurable) of silence if there is still a signal detected but decode errors. This gives the system a chance to reaquire the transmission before splitting the transmission into separate audio files. This metric should be measured and optimized for as well. The goal is to provide consistent audio decode that rapidly locks onto bursty PTT transmissions and does not interrupt long transmissions. This is done with a signal that may be weaker than is optimal which is why so much optimization and error correction is needed"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Continuous Audio During Decode Errors (Priority: P1)

As a radio monitor listening to a P25 conventional channel, I want the audio decoder to maintain audio continuity during brief decode errors so that I hear complete transmissions without gaps or premature endings that split a single transmission into multiple audio files.

**Why this priority**: This is the core user experience issue. Premature audio stops during active transmissions cause users to miss critical information and create fragmented recordings that are difficult to review. This directly impacts the primary value of the software.

**Independent Test**: Can be tested by playing a baseband recording with known signal dropouts and measuring whether audio segments are correctly merged vs. incorrectly split. Delivers continuous audio output for complete transmissions.

**Acceptance Scenarios**:

1. **Given** an active transmission with 3+ seconds of valid audio decoded, **When** decode errors occur for less than 0.25 seconds while signal energy remains high, **Then** audio recording continues without creating a new audio segment
2. **Given** an active transmission experiencing brief decode errors, **When** valid frames resume within the configurable holdover period, **Then** the audio segment includes all successfully decoded frames as one continuous recording
3. **Given** an active transmission, **When** decode errors persist beyond the configurable holdover period AND signal energy drops to silence levels, **Then** the audio segment ends appropriately (this is a true transmission end)

---

### User Story 2 - Fast Lock on Bursty PTT Transmissions (Priority: P1)

As a radio monitor, I want the decoder to quickly lock onto new transmissions when push-to-talk (PTT) is activated so that I capture the beginning of each transmission including the first few words spoken.

**Why this priority**: Missing the start of transmissions means missing critical call information (who is speaking, what unit is responding). This is equally important to audio continuity for the user experience.

**Independent Test**: Can be tested by measuring LDU count and audio start time on short bursty transmissions. Delivers more complete audio capture at transmission start.

**Acceptance Scenarios**:

1. **Given** a silent channel (no signal for 500ms+), **When** a new transmission begins with PTT activation, **Then** the decoder acquires sync and produces valid audio within 200ms of signal presence
2. **Given** rapid back-to-back transmissions (gaps under 1 second), **When** a new transmission starts, **Then** the decoder reacquires sync without missing the first voice frame (LDU)
3. **Given** a weak signal at transmission start, **When** signal strength is above minimum decode threshold, **Then** first LDU capture rate is at least 80%

---

### User Story 3 - Configurable Holdover Period (Priority: P2)

As a system administrator, I want to configure the audio holdover period so that I can tune the decoder behavior for different channel conditions and use cases.

**Why this priority**: Different deployment scenarios (busy urban channels vs. rural, strong vs. weak signals) benefit from different holdover settings. This enables users to optimize for their specific environment.

**Independent Test**: Can be tested by changing the configuration value and verifying decoder behavior changes accordingly. Delivers user control over audio continuity behavior.

**Acceptance Scenarios**:

1. **Given** the holdover period is configurable, **When** a user sets holdover to 0.10 seconds, **Then** audio segments end more quickly after decode errors begin
2. **Given** the holdover period is configurable, **When** a user sets holdover to 0.25 seconds (maximum), **Then** the decoder waits longer before ending audio segments
3. **Given** no explicit configuration, **When** the decoder is used with default settings, **Then** a reasonable default holdover period (0.18 seconds / 1 LDU frame) is applied

---

### User Story 4 - Measurement and Optimization Metrics (Priority: P2)

As a developer or advanced user, I want to measure audio continuity metrics so that I can verify decoder performance and tune parameters for optimal results.

**Why this priority**: Without measurement capability, optimization is guesswork. This enables data-driven tuning and validation of improvements.

**Independent Test**: Can be tested by running the test tools on sample recordings and examining the output metrics. Delivers visibility into decoder performance.

**Acceptance Scenarios**:

1. **Given** a baseband recording is processed, **When** analysis completes, **Then** metrics include: total audio segments, false splits (transmission interrupted during active signal), segment durations, and LDU capture rate
2. **Given** multiple test recordings, **When** comparative analysis runs, **Then** results show improvement/regression in audio continuity metrics
3. **Given** a recording with known characteristics, **When** processed with different parameter settings, **Then** metrics show the impact of each parameter change

---

### Edge Cases

- What happens when signal drops below decode threshold but above noise floor (marginal signal)? The decoder should use the configurable holdover period before ending audio.
- What happens when multiple rapid PTT activations occur (users stepping on each other)? Each distinct transmission should be captured; brief overlaps should not cause excessive segment splitting.
- What happens when decode errors occur at the very start of a transmission? The decoder should still attempt to capture subsequent valid frames without requiring a new sync acquisition.
- What happens when the configured holdover exceeds the actual gap between transmissions? The decoder should still correctly detect transmission boundaries based on signal energy, not just decode success.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST maintain audio segment continuity during decode errors when RF signal energy indicates an active transmission
- **FR-002**: System MUST support a configurable holdover period (0 to 0.25 seconds) that determines how long to wait during decode errors before ending an audio segment
- **FR-003**: System MUST use signal energy detection (not just decode success) to distinguish between transmission-in-progress with errors versus transmission ended
- **FR-004**: System MUST acquire sync and begin audio decode within 200ms of transmission start on a previously silent channel
- **FR-005**: System MUST reacquire sync within 100ms when transitioning between rapid back-to-back transmissions (gaps under 1 second)
- **FR-006**: System MUST provide a default holdover period of 0.18 seconds (approximately 1 LDU frame duration) when not explicitly configured
- **FR-007**: System MUST NOT insert audible artifacts (clicks, pops, static) into the audio stream during the holdover period; silence or the last valid audio frame should be used
- **FR-008**: System MUST track and expose metrics for audio continuity analysis: segment count, false split count, average segment duration, LDU capture rate
- **FR-009**: System MUST work effectively with weak signals where decode errors are more frequent due to reduced signal-to-noise ratio
- **FR-010**: System MUST correctly end audio segments when actual transmission ends are detected (signal energy drops to silence levels)

### Key Entities

- **Audio Segment**: A continuous recording of decoded audio from transmission start to end; should correspond 1:1 with actual radio transmissions
- **Transmission Boundary**: The point where signal energy transitions from silence to active (start) or active to silence (end)
- **Holdover Period**: The configurable time to maintain audio segment continuity during decode errors when signal is still present
- **LDU (Logical Data Unit)**: The P25 voice frame containing 180ms of audio; the fundamental unit of decoded voice

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: False audio segment splits during active transmissions reduced by 50% compared to current baseline
- **SC-002**: First LDU capture rate on transmission start is at least 85% (currently measuring around 80%)
- **SC-003**: Sync acquisition time on cold start is under 200ms for 90% of transmissions
- **SC-004**: Total decoded audio duration increases by at least 2% compared to current baseline (capturing more of each transmission)
- **SC-005**: No increase in audio artifacts or degradation in audio quality during holdover periods
- **SC-006**: User-configurable holdover period can be adjusted within the specified range (0-0.25 seconds) and takes effect without application restart

## Assumptions

- The existing signal energy detection mechanism in the decoder can reliably distinguish between active signal and silence
- The 0.25 second maximum holdover period is sufficient for most decode error recovery scenarios without causing excessive latency
- Audio segment management occurs in the audio pipeline after decoding, not in the decoder itself
- The test infrastructure from the previous optimization work can be extended to measure continuity metrics
- Performance impact of holdover logic is negligible compared to the existing decode processing

## Out of Scope

- Changes to the audio codec itself
- Support for Phase 2 (this is Phase 1 specific)
- Real-time display of decode quality metrics in the UI (metrics are for testing/tuning only)
- Automatic adaptive tuning of holdover period based on signal conditions (future enhancement)
