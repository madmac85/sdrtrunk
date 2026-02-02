# Feature Specification: P25 LSM v2 Comprehensive Decode Optimization

**Feature Branch**: `011-comprehensive-decode-optimization`
**Created**: 2026-02-02
**Status**: Draft
**Input**: User description: "There are still issues with transmission continuity. I have added an additional_samples section with further decode samples. Ignore anything related to Derry fire as that used the C4FM decoder. There is a significant amount of artifacting in the decoded audio. Please conduct further research into these new samples including the nearly 2 hour long Roc West sample of IQ data. Also note, this channel will never have encrypted audio, or a control signal. Anything decoded to that effect is really an error. Full dedicated research is needed to more comprehensively optimize the decode for audio quality, demodulation quality, error correction, and continuity. All options for optimization are on the table, including the audio decode library if indicated from research. Aggressive, potentially CPU intensive correction methods should be considered for error correction. What if multiple tuners fixed on the same freq to try and mitigate errors or inconsistencies? Maybe this would help. A full planning and exploration following by exploration should be undertaken."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Artifact-Free Audio Playback (Priority: P1)

As a radio monitor listening to decoded P25 transmissions, I want the audio to play cleanly without digital artifacts, buzzing, clicking, or distortion so that I can clearly understand the communications.

**Why this priority**: Audio artifacts directly impact the core value proposition of the decoder. If the audio is unlistenable due to artifacts, the decoder fails its primary purpose regardless of how many frames are decoded.

**Independent Test**: Can be tested by playing back decoded audio from test recordings and evaluating for the presence of artifacts using both subjective listening tests and objective audio quality metrics.

**Acceptance Scenarios**:

1. **Given** a transmission with good signal strength, **When** decoded by the v2 decoder, **Then** the audio plays without audible artifacts, buzzing, or clicking sounds
2. **Given** a transmission with marginal signal conditions, **When** decoded by the v2 decoder, **Then** artifact occurrences are minimized and isolated rather than persistent
3. **Given** a transmission with brief decode errors, **When** the decoder recovers, **Then** no audible "digital garbage" is inserted into the audio stream
4. **Given** the dedicated Roc West channel recording (~1 hour), **When** processed end-to-end, **Then** 95% of audio segments are free from persistent artifacts

---

### User Story 2 - Continuous Transmission Decode (Priority: P1)

As a radio monitor, I want complete transmissions to decode as single continuous audio segments without being fragmented into multiple pieces so that conversations flow naturally and are easy to follow.

**Why this priority**: Call fragmentation creates a disjointed listening experience and causes problems for streaming services like RDIO. This is tied with audio quality as the core user experience issue.

**Independent Test**: Can be tested by counting audio segments produced per known transmission and comparing to expected count (ideally 1:1 ratio).

**Acceptance Scenarios**:

1. **Given** a single continuous transmission, **When** processed by the decoder, **Then** exactly one audio segment is produced
2. **Given** a transmission with brief decode errors mid-stream, **When** signal remains present, **Then** the audio segment remains continuous (not split)
3. **Given** back-to-back transmissions from different units, **When** there is a clear silence gap, **Then** separate audio segments are correctly produced
4. **Given** the new additional_samples recordings, **When** processed, **Then** fragmentation ratio is below 1.1 (less than 10% excess segments)

---

### User Story 3 - Accurate Decode Without False Detections (Priority: P2)

As a radio monitor of a dedicated conventional channel (no control channel, no encryption), I want the decoder to avoid producing false encrypted audio detections or control channel detections so that I don't miss audio due to incorrect state handling.

**Why this priority**: False detections of encryption or control signals on a known voice-only channel cause audio to be discarded or muted incorrectly. This is a configuration-specific but important issue.

**Independent Test**: Can be tested by processing known voice-only channel recordings and verifying no encrypted call or control channel events are generated.

**Acceptance Scenarios**:

1. **Given** a dedicated voice channel with no encryption capability, **When** processing transmissions, **Then** no encrypted audio events are generated
2. **Given** a dedicated voice channel with no control signal, **When** processing transmissions, **Then** no control channel state transitions occur
3. **Given** decode errors that produce garbage data, **When** parsed for message type, **Then** false encryption/control detections are filtered or rejected

---

### User Story 4 - Maximized LDU Frame Recovery (Priority: P2)

As a radio monitor, I want the decoder to recover as many LDU (voice) frames as possible from the signal, even in marginal conditions, so that I get the most complete audio coverage.

**Why this priority**: More recovered frames means more audio content. This builds on the existing optimization work but pushes further with aggressive error correction.

**Independent Test**: Can be tested by comparing LDU counts between decoder versions and against theoretical maximum based on transmission duration.

**Acceptance Scenarios**:

1. **Given** a recording with known transmission count and duration, **When** processed, **Then** LDU recovery rate is within 10% of theoretical maximum
2. **Given** marginal signal conditions, **When** aggressive error correction is applied, **Then** LDU count improves over baseline without increasing artifact rate
3. **Given** the 1-hour Roc West sample, **When** fully processed, **Then** decode efficiency exceeds 90% of expected LDUs

---

### User Story 5 - Research-Driven Optimization (Priority: P3)

As a developer/researcher, I want comprehensive analysis tools and metrics to identify decode quality issues and measure the impact of optimizations so that improvements can be validated objectively.

**Why this priority**: Without measurement infrastructure, optimization work is guesswork. This enables all other improvements.

**Independent Test**: Can be tested by running analysis tools on sample files and verifying metrics are produced and meaningful.

**Acceptance Scenarios**:

1. **Given** any baseband recording, **When** analysis tools are run, **Then** detailed metrics on LDU count, error rates, sync losses, and audio quality are produced
2. **Given** optimization changes, **When** compared using analysis tools, **Then** quantitative improvement/regression is clearly measurable
3. **Given** the additional_samples directory, **When** batch analysis is run, **Then** a comprehensive quality report is generated

---

### Edge Cases

- What happens with extremely weak signals near the noise floor? Aggressive error correction should attempt recovery without producing worse artifacts.
- What happens when interference causes burst errors? The decoder should ride through brief interference without fragmenting calls.
- What happens when PLL drift exceeds normal bounds? Recovery should be attempted before cold-start reset.
- What happens with atypical modulation characteristics? The demodulator should adapt to real-world signal variations.
- What happens when the JMBE codec produces bad frames? Frame-level error detection and concealment should be considered.

## Requirements *(mandatory)*

### Functional Requirements

#### Audio Quality
- **FR-001**: System MUST minimize audible artifacts in decoded audio including clicks, buzzes, and digital noise
- **FR-002**: System MUST NOT insert "garbage audio" frames when decode errors occur; silence or frame repetition is preferred
- **FR-003**: System MUST implement audio frame error detection to identify and handle corrupted IMBE frames

#### Transmission Continuity
- **FR-004**: System MUST maintain call continuity during brief decode errors when RF signal energy indicates active transmission
- **FR-005**: System MUST NOT fragment single transmissions into multiple audio segments due to transient decode failures
- **FR-006**: System MUST correctly identify true transmission boundaries based on signal characteristics, not just decode state

#### Error Correction & Recovery
- **FR-007**: System MUST apply aggressive error correction to maximize frame recovery even at higher CPU cost
- **FR-008**: System MUST implement enhanced sync recovery strategies for marginal signal conditions
- **FR-009**: System MUST optimize NID (Network ID) decoding to improve message recovery rate

#### Channel Configuration
- **FR-010**: System MUST support configuration to disable encryption detection for known unencrypted channels
- **FR-011**: System MUST support configuration to disable control channel detection for dedicated voice channels
- **FR-012**: System MUST filter/reject false positive detections that result from decode errors producing random data

#### Demodulation Quality
- **FR-013**: System MUST optimize PLL tracking for improved symbol timing recovery
- **FR-014**: System MUST implement fade detection and recovery for signal dropouts
- **FR-015**: System MUST adapt to real-world signal variations including frequency offset and modulation deviations

#### Research & Analysis
- **FR-016**: System MUST provide comprehensive decode quality metrics for research and optimization
- **FR-017**: System MUST support batch analysis of sample recordings with detailed reporting
- **FR-018**: System MUST track error rates at multiple levels: sync, NID, message, and audio frame

### Key Entities

- **LDU (Logical Data Unit)**: A voice frame containing 9 IMBE audio frames representing ~180ms of audio
- **IMBE Frame**: A single vocoder frame (20ms of audio) that can be individually corrupted or valid
- **Transmission**: A continuous radio transmission bounded by carrier on/off, may contain multiple LDUs
- **Audio Segment**: The decoded audio output corresponding to one transmission
- **Sync Pattern**: The 48-bit pattern used to identify frame boundaries in the P25 signal
- **NID (Network Identifier)**: 64-bit field containing NAC and DUID used to identify message type
- **Signal Energy**: RF power level used to detect transmission presence independent of decode success

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Audio artifact rate reduced by 80% compared to current baseline (measured by manual listening evaluation and/or objective metrics on test samples)
- **SC-002**: Call fragmentation ratio below 1.1 (less than 10% excess audio segments per transmission)
- **SC-003**: LDU recovery rate within 10% of theoretical maximum for good signal conditions
- **SC-004**: Zero false encryption or control channel detections on known voice-only channel recordings
- **SC-005**: Research report produced with comprehensive analysis of all sample files identifying root causes and optimization opportunities
- **SC-006**: 95% of transmissions in the 1-hour Roc West sample produce clean, continuous audio segments
- **SC-007**: No regression in decode performance on existing test samples from previous optimization work

## Assumptions

- The dedicated channel being optimized for (Rockingham County PD West) operates as conventional P25 voice only, without encryption or trunking control
- Any detection of encryption or control channel states on this channel represents a decoder error, not actual encrypted/control traffic
- The JMBE codec may be a source of audio artifacts and should be investigated as part of the research
- CPU-intensive error correction methods are acceptable if they improve audio quality
- The ~1 hour Roc West sample (additional_samples) provides representative real-world signal conditions
- Sample files named with "Derry fire" reference use C4FM modulation and should be excluded from LSM v2 analysis
- Multiple tuner diversity (if pursued) would require significant architectural changes and may be deferred to a future enhancement

## Out of Scope

- C4FM decoder optimization (separate decoder type)
- Trunking/control channel functionality
- Encryption support or encrypted audio decoding
- Real-time streaming service integration changes
- UI/UX changes beyond configuration options
- Hardware tuner modifications
- Phase 2 (TDMA) decoder changes

## Research Areas to Investigate

The following areas should be thoroughly researched during the planning phase:

1. **Audio Codec Analysis**: Investigate JMBE codec behavior with corrupted input frames; explore alternative codec implementations or frame error concealment
2. **Error Correction Strategies**: Research Reed-Solomon enhancement, soft-decision decoding, iterative decoding approaches
3. **Sync Detection Enhancement**: Analyze sync failure patterns and optimize thresholds/strategies for weak signals
4. **Demodulation Improvements**: Investigate PLL tracking algorithms, carrier recovery, and adaptive equalization
5. **Multi-Tuner Diversity**: Research feasibility and expected gains from combining multiple receiver streams
6. **Frame Error Concealment**: Investigate techniques for masking bad frames (repetition, interpolation, silence)
7. **Objective Audio Quality Metrics**: Research PESQ, POLQA, or simpler metrics for automated quality assessment
