# Feature Specification: P25 LSM v2 Call Continuity Improvement

**Feature Branch**: `010-call-continuity`
**Created**: 2026-01-31
**Status**: Draft
**Input**: User description: "We are seeing some good improvement, however, we are seeing calls 'Broken up' when using the v2 decoder. When listening using services like RDIO this creates a very choppy listening experience. We need to better at keeping calls active instead of breaking calls up"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Continuous Call Audio Without Fragmentation (Priority: P1)

As a radio monitor using streaming services like RDIO, I want calls to play as continuous audio without being broken into multiple fragments so that I have a smooth listening experience without choppy interruptions.

**Why this priority**: This is the core user experience issue. Fragmented calls create a poor listening experience where users hear repeated start/stop audio artifacts, miss words at fragment boundaries, and find it difficult to follow conversations. This directly impacts the value proposition for streaming listeners.

**Independent Test**: Can be tested by comparing the number of audio segments produced for a single radio transmission with the v2 decoder vs. the original LSM decoder. A single transmission should produce a single audio segment in most cases.

**Acceptance Scenarios**:

1. **Given** a continuous radio transmission of 10+ seconds, **When** the v2 decoder processes it, **Then** a single audio segment is produced (not multiple fragments)
2. **Given** a transmission with brief decode errors (under 500ms), **When** the decoder encounters these errors, **Then** the audio segment remains continuous without splitting
3. **Given** a transmission being streamed to RDIO or similar service, **When** the transmission plays, **Then** the listener hears continuous audio without choppy start/stop artifacts

---

### User Story 2 - Appropriate Call Boundaries (Priority: P1)

As a radio monitor, I want the decoder to correctly identify when a call actually ends vs. when it's experiencing temporary decode issues so that calls only split at true transmission boundaries.

**Why this priority**: False call endings cause not only audio fragmentation but also incorrect metadata, repeated call notifications, and logging issues. This is equally critical to the listening experience.

**Independent Test**: Can be tested by processing recordings with known transmission boundaries and verifying audio segments align with actual transmission starts/ends.

**Acceptance Scenarios**:

1. **Given** a single radio transmission, **When** signal energy remains above silence threshold, **Then** the decoder maintains the call as active even during decode errors
2. **Given** back-to-back transmissions from different units, **When** there is a true silence gap between them, **Then** two separate audio segments are correctly produced
3. **Given** a transmission with signal fading, **When** signal recovers within 1 second, **Then** the original audio segment continues (not a new call)

---

### User Story 3 - Reduced Call Start/Stop Events (Priority: P2)

As a system integrator using the API for call notifications, I want to receive accurate call start/stop events that correspond to actual transmissions so that my downstream systems aren't flooded with false events.

**Why this priority**: Fragmented calls generate excessive API events which impact integrations, logging, and analytics. While secondary to listening experience, this affects professional deployments.

**Independent Test**: Can be tested by counting call start/end events per transmission and comparing to expected values.

**Acceptance Scenarios**:

1. **Given** a 30-second transmission, **When** processed by the v2 decoder, **Then** exactly one call start and one call end event are generated
2. **Given** multiple transmissions in a recording, **When** comparing v2 to LSM decoder events, **Then** v2 produces equal or fewer call events (not more)
3. **Given** a transmission with decode errors in the middle, **When** signal remains present, **Then** no intermediate call start/end events occur

---

### Edge Cases

- What happens when signal drops to near-silence levels but transmission continues? The decoder should use a grace period based on signal energy, not just decode success.
- What happens with very short transmissions (under 500ms)? These should still produce valid audio segments without being discarded or merged incorrectly.
- What happens when a new transmission starts immediately after another ends? Distinct transmissions should remain separate even with minimal gap.
- What happens during rapid PTT keying (talkover)? Each actual transmission should be captured, but brief overlaps shouldn't cause excessive fragmentation.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST maintain audio segment continuity during decode errors when RF signal energy indicates an active transmission
- **FR-002**: System MUST NOT create new audio segments when decode temporarily fails but signal energy remains above the silence threshold
- **FR-003**: System MUST use signal energy as the primary indicator for transmission boundaries, not decode success/failure
- **FR-004**: System MUST maintain a holdover period (grace period) before ending a call when decode errors occur during active signal
- **FR-005**: System MUST correctly distinguish between transmission-ending silence and mid-transmission decode errors
- **FR-006**: System MUST produce fewer or equal audio segments compared to the original LSM decoder for the same input
- **FR-007**: System MUST generate call start/end events that accurately correspond to actual transmission boundaries
- **FR-008**: System MUST NOT insert audible gaps or artifacts at points where decode errors occurred but transmission continued
- **FR-009**: System MUST work correctly with streaming services (RDIO, Broadcastify, etc.) producing smooth continuous audio

### Key Entities

- **Audio Segment**: A continuous recording representing a single radio transmission from start to end
- **Call**: The logical unit representing a radio transmission, associated with one audio segment
- **Transmission Boundary**: The point where signal energy transitions from silence to active (start) or active to silence (end)
- **Holdover Period**: The time to wait during decode errors before concluding a transmission has ended
- **Signal Energy**: The RF energy level used to determine if a transmission is active regardless of decode success

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Audio segment fragmentation reduced by 80% compared to current v2 baseline (measured as: segments per transmission should approach 1.0)
- **SC-002**: Call start/end events per transmission reduced to match or improve upon original LSM decoder
- **SC-003**: No increase in missed audio (total decoded audio duration should not decrease)
- **SC-004**: 95% of single transmissions produce exactly one audio segment
- **SC-005**: Streaming listeners report smooth continuous audio without choppy playback
- **SC-006**: No regression in LDU decode rate or audio quality

## Assumptions

- The transmission boundary detection and signal energy monitoring from previous optimizations is functioning correctly
- The issue is that boundary resets or other v2 mechanisms are prematurely ending calls
- The audio segment management layer can be influenced by decoder state signals
- The holdover/grace period mechanism from spec 003 may need adjustment or is not being applied correctly
- The problem may be related to how cold-start resets interact with active audio segments

## Out of Scope

- Changes to streaming service integrations themselves
- Audio codec modifications
- Phase 2 decoder changes
- Real-time UI feedback for call continuity (metrics are for testing only)
- Automatic adaptive tuning (future enhancement)
