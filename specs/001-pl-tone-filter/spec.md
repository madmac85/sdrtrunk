# Feature Specification: PL Tone (CTCSS) Filter for Analog FM Channels

**Feature Branch**: `001-pl-tone-filter`
**Created**: 2026-01-24
**Status**: Complete
**Input**: User description: "I want the ability to filter analog FM channels with a PL tone. For example, 114.8 PL. If this tone is not detected then the squelch will not open and will not decode the audio. This should be specified for the channel in the playlist editor. In theory, you could have multiple channels on the same freq with different PL codes that would get different audio. If no PL is specified then all audio will be decoded (current behavior)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure PL Tone on NBFM Channel (Priority: P1)

A user configures an NBFM channel in the playlist editor with a specific PL (CTCSS) tone frequency (e.g., 114.8 Hz). When monitoring that channel, audio is only decoded and output when the specified PL tone is present on the received signal. When the PL tone is absent (even if carrier is present), the squelch remains closed and no audio is produced.

**Why this priority**: This is the core feature — without PL tone-gated squelch, the entire feature has no value. It enables users to filter out unwanted transmissions on shared frequencies.

**Independent Test**: Can be fully tested by configuring one NBFM channel with a PL tone and verifying that audio only passes when that tone is present on the signal.

**Acceptance Scenarios**:

1. **Given** an NBFM channel configured with PL tone 114.8 Hz, **When** a transmission occurs on that frequency with 114.8 Hz PL tone present, **Then** the squelch opens and audio is decoded normally.
2. **Given** an NBFM channel configured with PL tone 114.8 Hz, **When** a transmission occurs on that frequency without any PL tone (or with a different PL tone), **Then** the squelch remains closed and no audio is decoded.
3. **Given** an NBFM channel configured with PL tone 114.8 Hz, **When** a transmission begins with the correct PL tone and the tone drops mid-transmission, **Then** the squelch closes and audio stops.

---

### User Story 2 - Multiple Channels Same Frequency Different PL Tones (Priority: P2)

A user configures multiple NBFM channels on the same frequency, each with a different PL tone. Each channel independently decodes only the audio matching its configured PL tone. This allows simultaneous monitoring of multiple users/groups sharing a single frequency via PL tone separation.

**Why this priority**: This extends the core feature to its full utility — separating multiple users on a shared frequency. It depends on P1 working correctly but adds significant monitoring capability.

**Independent Test**: Can be tested by creating two channels on the same frequency with different PL tones and verifying each channel only decodes audio with its corresponding tone.

**Acceptance Scenarios**:

1. **Given** two NBFM channels configured on the same frequency with PL tones 114.8 Hz and 127.3 Hz respectively, **When** a transmission occurs with 114.8 Hz PL tone, **Then** only the first channel decodes audio and the second remains silent.
2. **Given** two NBFM channels configured on the same frequency with PL tones 114.8 Hz and 127.3 Hz respectively, **When** a transmission occurs with 127.3 Hz PL tone, **Then** only the second channel decodes audio and the first remains silent.

---

### User Story 3 - No PL Tone Configured (Backward Compatibility) (Priority: P1)

A user configures an NBFM channel without specifying a PL tone (the default). The channel behaves exactly as it does today — all audio that passes the noise squelch is decoded regardless of whether a PL tone is present.

**Why this priority**: Equal to P1 because backward compatibility is essential — existing configurations must continue to work unchanged.

**Independent Test**: Can be tested by verifying an NBFM channel with no PL tone configured behaves identically to current behavior.

**Acceptance Scenarios**:

1. **Given** an NBFM channel with no PL tone configured, **When** a transmission occurs on that frequency (with or without any PL tone), **Then** audio is decoded normally based on noise squelch alone (current behavior).
2. **Given** an existing playlist with NBFM channels saved before this feature existed, **When** the playlist is loaded, **Then** all channels default to no PL tone filtering and behave as before.

---

### User Story 4 - PL Tone Selection in Playlist Editor (Priority: P1)

A user opens the playlist editor for an NBFM channel and selects a PL tone from the standard list of CTCSS frequencies. The selection is saved with the channel configuration and persisted across application restarts.

**Why this priority**: Essential for usability — users need a way to configure the PL tone. Without a UI control, the feature is inaccessible.

**Independent Test**: Can be tested by selecting a PL tone in the editor, saving, restarting the application, and verifying the PL tone setting is preserved.

**Acceptance Scenarios**:

1. **Given** the playlist editor is open for an NBFM channel, **When** the user selects a PL tone from the available options, **Then** the selected tone is displayed and associated with the channel.
2. **Given** an NBFM channel with a PL tone configured, **When** the user saves the playlist and reopens the editor, **Then** the previously selected PL tone is shown.
3. **Given** an NBFM channel with a PL tone configured, **When** the user selects "None" (or clears the PL tone), **Then** the channel reverts to unfiltered behavior.

---

### Edge Cases

- What happens when the received PL tone frequency is slightly off from the configured value (e.g., transmitter drift)? The system should use a detection window of approximately +/- 1-2% to account for transmitter frequency tolerance.
- What happens when the PL tone is very weak relative to the voice audio? The detector should have a minimum threshold for tone amplitude to avoid false positives from noise.
- What happens when two PL tones are present simultaneously (unusual but possible with interference)? The system should detect tone presence independently — if the configured tone is detected, audio passes regardless of other tones present.
- What happens during the transition period when a PL tone first appears or disappears? A brief detection delay (on the order of 100-300ms) is acceptable to confirm tone presence/absence and avoid choppy audio.
- What happens with the AM decoder? PL tone filtering applies only to NBFM channels. AM channels do not use PL tone filtering since CTCSS is an FM sub-audible tone system.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow users to configure a PL (CTCSS) tone frequency for any NBFM channel in the playlist editor.
- **FR-002**: System MUST support all 38 standard CTCSS tone frequencies (67.0 Hz through 250.3 Hz per EIA/TIA standard).
- **FR-003**: System MUST gate audio output based on PL tone presence when a PL tone is configured — squelch opens only when both the noise squelch threshold is met AND the configured PL tone is detected.
- **FR-004**: System MUST pass all audio (current behavior) when no PL tone is configured for a channel.
- **FR-005**: System MUST persist the PL tone configuration as part of the channel playlist configuration.
- **FR-006**: System MUST allow multiple channels on the same frequency with different PL tone configurations, each independently gating audio based on its own configured tone.
- **FR-007**: System MUST detect the configured PL tone with a frequency tolerance of at least +/- 2% to accommodate transmitter drift.
- **FR-008**: System MUST remove (filter out) the sub-audible PL tone from the decoded audio output so it is not audible to the user.
- **FR-009**: System MUST maintain backward compatibility — existing playlists without PL tone settings load and operate unchanged.
- **FR-010**: System MUST provide a "None" or empty option in the PL tone selector to explicitly indicate no PL tone filtering.

### Key Entities

- **CTCSS Tone**: A sub-audible tone frequency (67.0–250.3 Hz) transmitted continuously during voice transmissions. Identified by its frequency value. Associated with a channel configuration as an optional filter parameter.
- **PL Tone Configuration**: An optional setting on an NBFM channel that specifies which CTCSS tone frequency must be present for audio to be decoded. When absent/none, the channel uses noise squelch only.

## Assumptions

- The standard set of 38 EIA/TIA CTCSS frequencies is sufficient; non-standard tones are not required.
- DCS (Digital Code Squelch) is out of scope for this feature — only analog CTCSS tones are addressed.
- The PL tone detector operates on demodulated FM audio (post-FM demodulation, pre-noise squelch) where the sub-audible tone is present in the baseband signal.
- A detection window of approximately 200-300ms of tone presence is acceptable before confirming tone detection (balances responsiveness vs. false positives).
- The existing noise squelch continues to operate as a first-stage gate — PL tone detection is a second-stage gate applied after noise squelch passes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can configure a PL tone for an NBFM channel and have audio correctly filtered within one monitoring session, with no false audio passes when the wrong PL tone is present.
- **SC-002**: Two channels on the same frequency with different PL tones correctly separate audio 100% of the time when transmissions do not overlap.
- **SC-003**: Existing NBFM channels with no PL tone configured produce identical audio output to the pre-feature behavior (zero regression).
- **SC-004**: PL tone detection responds within 500ms of tone onset/offset — audio begins within 500ms of the PL tone appearing and stops within 500ms of it disappearing.
- **SC-005**: PL tone configuration survives application restart — settings persist correctly in the saved playlist.
