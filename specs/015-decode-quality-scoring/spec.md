# Feature Specification: Decode Quality Scoring Tool

**Feature Branch**: `015-decode-quality-scoring`
**Created**: 2026-03-06
**Status**: Draft
**Input**: User description: "Build a comprehensive decode quality scoring tool for repeatable A/B comparison of control vs test builds across multiple samples, frequencies, and modulations."

## Context

Current decoder comparison testing relies on ad-hoc one-off LDU count comparisons. This makes it difficult to determine whether a code change is actually an improvement — especially when changes help one channel but regress another. The user has observed that the pre-merge build (`sdrtrunk-pre-merge`) has better audio continuity on some channels, while the current build has loud distortion events on Derry Fire despite the sync guard fix. A systematic, repeatable scoring tool is needed to objectively compare decoder builds across a corpus of test recordings.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Quick LDU Comparison (Priority: P1)

A developer modifies the decoder and wants a fast signal that the change hasn't regressed LDU recovery across all test samples. They run the scoring tool in "quick" mode, which processes all baseband files through the appropriate decoder (determined from the playlist) and reports LDU counts for both control and test builds.

**Why this priority**: This is the minimum viable comparison — fast feedback during development. All other scoring metrics build on top of this.

**Independent Test**: Can be fully tested by running the tool against a single baseband file with two different decoder configurations and verifying it reports LDU counts for both.

**Acceptance Scenarios**:

1. **Given** a corpus of baseband WAV files and a playlist XML defining channel configurations, **When** the quick comparison is run, **Then** each file is processed through the correct decoder (C4FM, CQPSK, or CQPSK_V2) based on the playlist's modulation setting for that channel, and LDU counts are reported for both control and test.
2. **Given** a baseband file that matches a playlist channel by frequency, **When** the tool resolves the decoder, **Then** it uses the modulation type and NAC from the playlist configuration.
3. **Given** comparison results across multiple files, **When** the report is generated, **Then** it shows per-file LDU counts, per-file delta (test vs control), and an aggregate summary.

---

### User Story 2 - Full Audio Quality Comparison (Priority: P1)

A developer wants a comprehensive quality assessment of decoded audio beyond raw LDU counts. The tool decodes baseband files through JMBE to produce audio output, then scores the resulting audio on multiple dimensions: total audio duration, segment continuity, dispatch tone clarity, audio distortion events, and speech intelligibility.

**Why this priority**: LDU counts alone don't capture audio quality regressions like distortion, fragmented audio segments, or garbled speech. This is the "full test" that provides actionable quality metrics.

**Independent Test**: Can be tested by processing a known-good baseband file and verifying each audio metric is computed and reported.

**Acceptance Scenarios**:

1. **Given** a decoded set of audio files from a baseband recording, **When** the full scoring runs, **Then** it reports: total seconds of audio, number of audio segments (files), and average segment length.
2. **Given** audio from a fire department channel containing dispatch tones, **When** tone detection runs, **Then** it identifies clear single-pitch tones lasting at least 1 second (two-tone dispatch paging).
3. **Given** decoded audio files, **When** distortion detection runs, **Then** it counts audio artifacts: squeaks, squeals, chirps, and breaks of perfect digital silence (0-sample runs, not decoded silence).
4. **Given** decoded audio files, **When** speech-to-text processing runs locally, **Then** it counts the total number of words recognized, providing a direct measure of audio intelligibility.
5. **Given** a baseband recording and its decoded audio, **When** the signal-to-decode ratio is computed, **Then** it reports the ratio of decoded audio seconds to total seconds of detectable signal on the channel.

---

### User Story 3 - Multi-Sample Aggregate Report (Priority: P2)

A developer wants to see the overall impact of a change across the entire test corpus — not just individual files. The tool produces an aggregate comparison report showing per-channel and per-modulation summaries.

**Why this priority**: Individual file results can be noisy. Aggregate results across the corpus reveal systematic improvements or regressions.

**Independent Test**: Can be tested by running the tool on 3+ baseband files and verifying the aggregate report correctly summarizes per-channel and overall metrics.

**Acceptance Scenarios**:

1. **Given** results from multiple baseband files, **When** the aggregate report is generated, **Then** it groups results by channel (matching playlist channel name) and shows per-channel totals and deltas.
2. **Given** results spanning C4FM and CQPSK channels, **When** the report is generated, **Then** it includes a per-modulation-type summary showing whether the change helped or hurt each modulation type.
3. **Given** all scoring dimensions, **When** the final report is generated, **Then** it produces a clear text-based comparison table showing control vs test scores with improvement/regression indicators.

### Edge Cases

- What happens when a baseband file's frequency doesn't match any playlist channel? (Report as "unknown channel" with auto-detect modulation fallback)
- What happens when a baseband file contains no decodable P25 transmissions? (Report zero scores, don't fail)
- How are encrypted transmissions handled in audio scoring? (Excluded — they produce no decodable audio)
- What happens when the speech-to-text engine produces zero words from voice audio? (Report 0 words — may indicate severely garbled audio)
- How are very short audio segments (<100ms) handled? (Counted as segments but flagged as potential artifacts)

## Requirements *(mandatory)*

### Functional Requirements

#### Quick Mode (LDU Comparison)
- **FR-001**: The tool MUST accept a directory of baseband WAV files and a playlist XML as inputs
- **FR-002**: The tool MUST resolve each baseband file to a playlist channel by matching the frequency embedded in the filename to the playlist's channel frequency configuration
- **FR-003**: The tool MUST determine the correct decoder (C4FM, CQPSK, CQPSK_V2) and NAC from the matched playlist channel
- **FR-004**: The tool MUST accept control and test via two source directory paths (e.g., `--control /path/to/sdrtrunk-pre-merge --test /path/to/sdrtrunk`). Optionally, a `--control-ref <commit/branch>` flag may be used instead of `--control`, which builds the control from the specified git ref into a temporary worktree automatically. The tool processes each baseband file through the matched decoder for both control and test.
- **FR-005**: The tool MUST report per-file LDU counts for control and test, with delta and percentage change

#### Full Mode (Audio Quality Scoring)
- **FR-006**: The tool MUST decode baseband files through the JMBE codec to produce audio output files
- **FR-007**: The tool MUST measure total seconds of decoded audio across all output files per baseband input
- **FR-008**: The tool MUST count the number of separate audio segment files produced per baseband input (fewer is better — indicates better call continuity)
- **FR-009**: The tool MUST calculate average audio segment length in seconds (longer is better)
- **FR-010**: The tool MUST automatically detect fire department channels by matching the playlist channel name (containing "Fire" or "FD") and run dispatch tone detection on those channels — sustained single-pitch tones lasting at least 1 second (two-tone dispatch paging). Non-FD channels skip tone detection automatically.
- **FR-011**: The tool MUST detect and count audio distortion events: squeaks (brief high-frequency artifacts), squeals (sustained high-frequency artifacts), chirps (rapid frequency sweeps), and digital silence gaps (runs of exactly 0-valued samples, distinct from decoded quiet audio)
- **FR-012**: The tool MUST run local speech-to-text on decoded audio files and count total recognized words as an intelligibility metric
- **FR-013**: The tool MUST calculate signal-to-decode ratio: seconds of decoded audio divided by seconds of detectable signal presence in the baseband recording

#### Reporting
- **FR-014**: The tool MUST produce a text-based comparison report showing control vs test scores for each metric, per-file and in aggregate
- **FR-015**: The tool MUST clearly indicate improvements (↑) and regressions (↓) for each metric in the comparison
- **FR-016**: The tool MUST support being run from the command line with a single command for reproducibility
- **FR-017**: The tool MAY be implemented in any language or combination of languages — the only requirement is a single command to run and a text report as output
- **FR-018**: The report MUST include the preferred tuner name from the playlist configuration for each channel, so the user can identify which SDR hardware was used for each recording

### Key Entities

- **Baseband Recording**: WAV file containing raw I/Q samples from a specific channel. Filename encodes frequency, channel name, timestamp.
- **Playlist Channel**: XML configuration defining modulation type (C4FM/CQPSK/CQPSK_V2), NAC, frequency, channel name, and preferred tuner hardware identifier.
- **Audio Segment**: Individual decoded audio file output by the system for a single call or transmission.
- **Score Report**: Text output comparing all metrics between control and test across the full sample corpus.
- **Control Build**: The unmodified reference codebase (baseline for comparison).
- **Test Build**: The proposed modification being evaluated against the control.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The tool produces identical scores when run twice on the same input — results are fully deterministic and reproducible
- **SC-002**: A developer can run a full comparison across all available test samples with a single command and receive a complete report
- **SC-003**: The report clearly answers "did this change improve things?" for each channel and in aggregate — no ambiguity about direction of change
- **SC-004**: Quick mode (LDU-only) completes within 5 minutes for the existing sample corpus (~10 baseband files)
- **SC-005**: The tool correctly identifies the decoder type for each baseband file by cross-referencing the playlist — no manual decoder selection needed
- **SC-006**: Audio distortion events detected by the tool correlate with audible artifacts that a human listener would identify as quality problems
- **SC-007**: Speech-to-text word count differences between control and test correlate with perceived audio intelligibility differences

## Clarifications

### Session 2026-03-06

- Q: How does the tool differentiate control from test? → A: Two source directories passed as arguments (e.g., `--control /path/to/sdrtrunk-pre-merge --test /path/to/sdrtrunk`), with an optional `--control-ref <commit/branch>` that builds the control from a specified git ref automatically.

## Assumptions

- The playlist XML at `_SAMPLES/default-lsmv2.xml` defines the correct modulation and NAC for each channel and can be used as the reference configuration
- Baseband filenames follow the SDRTrunk naming convention: `YYYYMMDD_HHMMSS_[frequency]_[system]_[site]_[channel]_[index]_baseband.wav`
- The JMBE codec JAR is available at `/home/kdolan/GitHub/jmbe/codec/build/libs/jmbe-1.0.9.jar` for audio decoding
- A local speech-to-text solution is available or can be installed (e.g., Whisper, Vosk, or similar offline model)
- "Control" and "test" are specified as two source directory paths or via a git ref for the control; they differ only in the decoder code, not in the playlist or sample data
- The tool may be implemented in any language(s) — Java, Python, shell scripts, or a combination — whatever best fits each scoring task. The only constraint is a single entry-point command.
- Dispatch tone detection is automatically enabled for fire department channels, identified by playlist channel name containing "Fire" or "FD"
- Signal presence detection uses energy-based analysis of the baseband I/Q data to determine when a transmission is active
