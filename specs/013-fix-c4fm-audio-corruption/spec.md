# Feature Specification: Fix C4FM Dispatch Tone Corruption & Silent Audio Gaps

**Feature Branch**: `013-fix-c4fm-audio-corruption`
**Created**: 2026-02-17
**Status**: Draft
**Input**: User description: "Dispatch tone audio is sometimes getting corrupted on C4FM decoder — garbled digital audio instead of clear tones. Also hearing silent gaps in some audio. Both issues are regressions from upstream merge. Need root cause analysis and fix. Provide decoded MP3 comparison from pre-merge and post-merge C4FM decoder."

**Pre-Merge Reference**: Commit `aae1ebfe` (fork state before upstream merge at `5922f41a`)
**Current HEAD**: Commit `a820bca4` (includes upstream audio squeak fix from `de0e722f`)
**Gold Standard (ROC W)**: Commit `a820bca4` — any fix must preserve ROC W performance

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Dispatch Tones Decode Correctly (Priority: P1)

As a radio monitor listening to Derry Fire Department, I want dispatch alerting tones to be decoded as clear, recognizable tones so that I can hear station alerting without garbled or distorted audio.

**Why this priority**: Dispatch tones are critical alerting signals — garbled tones make it impossible to distinguish which station is being alerted. The user reports this is an all-or-nothing issue: tones are either fully correct or fully garbled, never partially corrupted. This is the primary regression reported.

**Independent Test**: Can be tested by processing the large Derry FD baseband recording through both the pre-merge and post-merge C4FM decoder, extracting audio at the known tone timestamps, and comparing spectral content.

**Acceptance Scenarios**:

1. **Given** the large Derry FD baseband recording and the bad tone timestamp (~12:56:50 EST, offset ~15,695s into file), **When** processed by the pre-merge C4FM decoder (commit `aae1ebfe`), **Then** the dispatch tone audio is clean with narrow spectral peaks at the expected frequencies (~344 Hz and ~914 Hz)
2. **Given** the same baseband segment, **When** processed by the current C4FM decoder (commit `a820bca4`), **Then** the tone audio is garbled with broadband harmonic artifacts, confirming the regression
3. **Given** a fix is applied to the C4FM decoder, **When** the same baseband segment is processed, **Then** dispatch tones are decoded cleanly, matching the pre-merge quality (narrow spectral peaks, RMS level within 3 dB of good tone reference)
4. **Given** the fix is applied, **When** the good tone segment (~09:51:11 EST, offset ~4,556s) is processed, **Then** it remains clean — no regression on already-working tones

---

### User Story 2 - Eliminate Silent Audio Gaps (Priority: P1)

As a radio monitor, I want continuous audio playback during voice transmissions without unexpected silent gaps so that I do not miss spoken content.

**Why this priority**: Silent gaps cause loss of spoken content, potentially missing critical dispatch information. The user reports this as a co-occurring regression from the same upstream merge.

**Independent Test**: Can be tested by processing the full Derry FD baseband recording and counting LDU recovery continuity — silent gaps correspond to missed LDU sequences.

**Acceptance Scenarios**:

1. **Given** the large Derry FD baseband recording, **When** processed by the pre-merge C4FM decoder, **Then** a baseline LDU count and continuity metric is established
2. **Given** the same recording processed by the current C4FM decoder, **When** LDU continuity is compared to the pre-merge baseline, **Then** any gap increases are identified and quantified
3. **Given** a fix is applied, **When** LDU continuity is measured, **Then** it matches or exceeds the pre-merge baseline (no new gaps introduced)

---

### User Story 3 - Produce Decoded Audio Comparison (Priority: P1)

As a developer investigating this regression, I want decoded MP3 audio output from both the pre-merge and post-merge C4FM decoders for the bad tone segment so that I can audibly and spectrally confirm the regression and verify the fix.

**Why this priority**: Audio comparison is the primary verification method. The user explicitly requests this deliverable to confirm root cause identification and fix effectiveness.

**Independent Test**: Can be tested by producing MP3 files and comparing their spectral characteristics — the good output should have narrow tonal peaks while the bad output has broadband artifacts.

**Acceptance Scenarios**:

1. **Given** the large Derry FD baseband recording and the bad tone timestamp (~12:56:50 EST), **When** the pre-merge C4FM decoder processes this segment, **Then** an MP3 file is produced showing clean dispatch tones
2. **Given** the same segment, **When** the current (post-merge) C4FM decoder processes it, **Then** an MP3 file is produced showing garbled audio
3. **Given** a fix is applied, **When** the same segment is decoded, **Then** an MP3 file is produced matching the pre-merge quality

---

### User Story 4 - Preserve ROC W Gold Standard (Priority: P1)

As a developer, I want any C4FM fix to not regress LSM/LSMv2 decoder performance on ROC W so that the existing gold standard is maintained.

**Why this priority**: ROC W is the validated baseline. The fix targets shared message framing infrastructure used by all decoder types, so regression testing is mandatory.

**Independent Test**: Can be tested by running the existing ROC W comparison test and verifying metrics match or exceed the gold standard.

**Acceptance Scenarios**:

1. **Given** the ROC W baseband recording, **When** processed after the fix, **Then** LSM v2 produces 434+ LDUs with 0 regressions (matching gold standard commit `a820bca4`)
2. **Given** the fix modifies shared decoder infrastructure, **When** all three decoder types (C4FM, LSM, LSM v2) are tested on both Derry and ROC W samples, **Then** no decoder regresses on any sample set

---

### Edge Cases

- What happens when a transmission starts with dispatch tones followed by voice? The fix must handle the tone-to-voice transition without introducing artifacts at the boundary.
- What happens when multiple rapid PTT transmissions occur in sequence? The fix must correctly re-sync between transmissions without causing gaps.
- What happens on other C4FM channels (user reports similar behavior observed elsewhere)? The fix should be general, not Derry-specific.
- What happens when the sync fix guard correctly prevents a false sync during message assembly? The original audio squeak bug must remain fixed.

## Requirements *(mandatory)*

### Functional Requirements

#### Root Cause Analysis
- **FR-001**: System MUST identify the specific code change from the upstream merge (commit `5922f41a`) that causes dispatch tone corruption on C4FM channels
- **FR-002**: System MUST verify the regression by comparing C4FM decoder output from the pre-merge code (commit `aae1ebfe`) against the current code (commit `a820bca4`) using the same baseband input
- **FR-003**: System MUST determine whether the tone corruption and silent gaps share a common root cause or are separate issues

#### Audio Comparison Deliverables
- **FR-004**: System MUST produce a decoded MP3 file of the bad tone segment from the pre-merge C4FM decoder showing correct audio
- **FR-005**: System MUST produce a decoded MP3 file of the same segment from the current C4FM decoder showing garbled audio
- **FR-006**: System MUST produce a decoded MP3 file of the same segment from the fixed C4FM decoder showing correct audio

#### Fix Implementation
- **FR-007**: System MUST fix the C4FM decoder so dispatch tones decode as clean tonal signals rather than broadband harmonic artifacts
- **FR-008**: System MUST fix silent audio gaps caused by missed voice frame sequences
- **FR-009**: System MUST preserve the original audio squeak fix behavior — false sync detections during valid message assembly must still be rejected
- **FR-010**: System MUST not introduce regressions on LSM or LSM v2 decoder paths

#### Regression Testing
- **FR-011**: System MUST pass ROC W gold standard comparison (LSM v2: 434+ LDUs, 0 regressions)
- **FR-012**: System MUST pass Derry FD comparison showing C4FM performance matching or exceeding pre-merge baseline
- **FR-013**: System MUST verify the original audio squeak scenario (false sync mid-message) remains handled correctly

### Key Entities

- **Dispatch Tone**: A two-tone sequential or simultaneous paging signal (e.g., 344 Hz + 914 Hz) used to alert specific fire stations; when correctly decoded, appears as narrow spectral peaks; when corrupted, appears as broadband 50 Hz harmonic comb characteristic of garbled voice codec output
- **LDU (Logical Data Unit)**: A P25 Phase 1 voice frame containing 9 voice codec frames (~180ms of audio); missed LDUs produce silent gaps
- **Sync Detection Guard**: The condition added in the audio squeak fix that suppresses new sync detections during active message assembly
- **Message Assembler**: State machine in the message framer that collects symbols after sync+NID validation to form complete P25 messages

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dispatch tones at the bad tone timestamp decode with spectral energy concentrated in 300-1000 Hz band (matching the good tone profile), not spread across 0-3000 Hz
- **SC-002**: The pre-merge decoder produces clean tone audio for the same segment, confirming the regression exists in the current code
- **SC-003**: After the fix, C4FM LDU count on the full large Derry FD recording matches or exceeds the pre-merge C4FM baseline
- **SC-004**: After the fix, no new silent gaps are introduced compared to the pre-merge baseline
- **SC-005**: ROC W LSM v2 performance remains at 434+ LDUs with 0 regressions
- **SC-006**: Three MP3 comparison files are produced (pre-merge, post-merge/broken, post-fix) demonstrating the regression and fix
- **SC-007**: The original audio squeak scenario (false sync interrupting valid message assembly) remains correctly handled after the fix

## Assumptions

- The large Derry FD baseband file (4h31m53s, ~3.04 GB) contains both the good tone (~09:51 EST) and bad tone (~12:57 EST) transmissions
- The good tone reference MP3 represents correct decode behavior — the same type of transmission decoded without corruption
- The bad tone reference MP3 represents the regression — the same type of transmission decoded with corruption
- The tone corruption is all-or-nothing per transmission (not intermittent within a single transmission), suggesting the issue is at sync acquisition or frame alignment, not ongoing tracking
- The fix should be general and benefit all C4FM channels, not just Derry FD
- The pre-merge C4FM decoder can be tested by checking out commit `aae1ebfe` and running the comparison test infrastructure
- Audio output from the test requires JMBE codec library and MP3 encoding support in the build
- The Derry FD channel uses C4FM modulation (FCC callsign WPJQ468, single-site conventional repeater, confirmed in investigation 012)

## Out of Scope

- Changes to the IMBE/JMBE voice codec itself
- LSM or LSM v2 decoder-specific changes (only shared infrastructure changes are in scope)
- Audio playback subsystem changes (the refactored AudioProvider/AudioChannel system)
- Hardware tuner or channelizer changes
- UI changes
- P25 Phase 2 (TDMA) decoder changes

## Sample Files Reference

### Large Baseband Recording

| File | Duration | Size | Start Time |
|------|----------|------|------------|
| `_SAMPLES/Derry FD/20260217_083515_..._baseband_large.wav` | 4h 31m 53s | 3.04 GB | 08:35:15 EST |

### Tone Reference MP3s

| File | Timestamp (EST) | Offset in Baseband | Quality |
|------|-----------------|-------------------|---------|
| `rocnh-7-1771339871_good_tone.mp3` | 09:51:11 | ~4,556s (~28%) | Clean two-tone (344 + 914 Hz) |
| `rocnh-7-1771351010_bad_tone.mp3` | 12:56:50 | ~15,695s (~96%) | Garbled (50 Hz harmonic comb) |
| `20260217_125656_..._FROM_81608.mp3` | 12:56:56 | ~15,701s (~96%) | Garbled (same transmission as bad tone, 95.3% correlated) |

### Audio Characteristics Comparison

| Property | Good Tone | Bad Tone |
|----------|-----------|----------|
| RMS Level | -6.4 dBFS | -22.4 dBFS |
| Peak Level | -0.8 dBFS | -9.2 dBFS |
| Dominant Frequencies | 344 Hz, 914 Hz | Broadband 50 Hz harmonics |
| Energy Band | 99.98% in 300-1000 Hz | Spread 0-3000 Hz |
| Spectral Flatness | 0.046 (very tonal) | 0.058 (noise-like) |

### Upstream Merge Change Analysis

| Changed File | Impact on C4FM |
|-------------|----------------|
| `P25P1MessageFramer.java` | **PRIMARY SUSPECT** — sync detection guard added |
| `P25P1DecoderC4FM.java` | Not changed |
| `P25P1DemodulatorC4FM.java` | Not changed |
| Audio playback subsystem (27 files) | Playback path only, unlikely decode impact |

### Pre-Merge Reference
- **Commit**: `aae1ebfe` (fork parent of merge `5922f41a`)
- **Description**: "Add UI controls for LSM v2 voice-only channel configuration"
