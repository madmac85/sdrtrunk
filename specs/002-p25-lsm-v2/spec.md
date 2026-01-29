# Feature Specification: P25 LSM v2 Demodulator for Conventional Channels

**Feature Branch**: `002-p25-lsm-v2`
**Created**: 2026-01-24
**Status**: Complete
**Input**: Improve LSM demodulator for conventional (non-trunked, PTT) P25 CQPSK channels. Add as a selectable "LSM v2" option alongside existing LSM for A/B comparison with real RF signals.

## Background

The existing P25 Phase 1 LSM (Linear Simulcast Modulation / CQPSK) demodulator (`P25P1DemodulatorLSM`) works well on trunked/simulcast channels with continuous carrier, but has cold-start issues on conventional (PTT-style) channels where the carrier turns on and off with each transmission.

### Root Causes Identified

1. **Stale Differential State**: `previousSymbolI/Q` is initialized to `0.7f` and persists across transmissions. When a new PTT transmission starts, the first differential demodulation uses stale state from the previous (potentially unrelated) transmission, producing corrupt initial symbols.

2. **Slow AGC Convergence**: The gain adjustment factor is `0.05f` (5% per symbol), meaning it takes ~20 symbols to converge to proper gain from a cold start. At 4800 symbols/sec, this is ~4ms of unreliable gain.

3. **Fixed PLL Gain**: The PLL gain is hardcoded at `0.1f` with no damping factor. On cold start, this is too aggressive for the initial noisy symbols but too slow for quick frequency acquisition.

4. **No Transmission Boundary Detection**: The demodulator has no mechanism to detect carrier-on/off transitions. State from a previous transmission bleeds into the next one.

5. **Single-Threshold Sync Detection**: The message framer uses a single threshold of `60` for sync detection. The C4FM decoder uses multi-tier sync (80/80/110) with coarse/fine optimization, which is more robust.

6. **Gardner TED on Corrupt Data**: The Gardner timing error detector operates immediately from the first symbol, using stale `previousSymbol` values. This produces large timing errors that pull the sample point away from optimal.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A/B Comparison of LSM vs LSM v2 (Priority: P1)

A user monitoring conventional P25 CQPSK channels (e.g., Rock County PD at 154.815 MHz) wants to compare the existing LSM demodulator against the improved v2 to determine which produces better decode rates. They select "LSM v2" from the modulation options in the channel configuration editor.

**Why this priority**: The user explicitly needs the ability to switch between LSM and LSM v2 for real-world testing. Without this, there's no way to validate that v2 actually improves decode performance.

**Independent Test**: Configure a P25 Phase 1 channel with known CQPSK conventional activity. Switch between LSM and LSM v2 modulation settings and observe decode event counts over a fixed time period.

**Acceptance Scenarios**:

1. **Given** a P25 Phase 1 channel configuration, **When** the user opens the decoder configuration editor, **Then** three modulation options are available: "Normal (C4FM)", "Simulcast (LSM)", and "Conventional (LSM v2)"
2. **Given** a channel configured with "Conventional (LSM v2)" modulation, **When** the channel is started, **Then** the system instantiates the v2 demodulator and begins processing I/Q samples
3. **Given** an existing playlist with a channel configured for CQPSK modulation, **When** the playlist is loaded, **Then** the channel continues to use the original LSM demodulator (backward compatible)

---

### User Story 2 - Improved Cold-Start Decode on Conventional Channels (Priority: P1)

A user monitoring a conventional P25 CQPSK channel experiences missed initial transmissions with the current LSM demodulator. After switching to LSM v2, the demodulator correctly resets its state on each new transmission and achieves sync more quickly, decoding the first message frame of each PTT transmission.

**Why this priority**: This is the core improvement that motivates the entire feature. Without cold-start fixes, LSM v2 would have no advantage over the existing LSM.

**Independent Test**: Monitor a conventional P25 CQPSK channel with LSM v2 and verify that messages are decoded from the beginning of transmissions, not just after the demodulator "warms up."

**Acceptance Scenarios**:

1. **Given** a channel configured with LSM v2 and a CQPSK transmission begins, **When** the first sync pattern appears in the signal, **Then** the demodulator detects sync within the first 2 data units (frames)
2. **Given** the demodulator has been idle (no signal) for 500ms+, **When** a new transmission begins, **Then** the differential state is reset and gain/PLL start from initial conditions appropriate for cold-start
3. **Given** consecutive short PTT transmissions on a conventional channel, **When** each transmission ends and the next begins, **Then** state from the previous transmission does not interfere with demodulation of the new transmission

---

### User Story 3 - Persistence of Modulation Setting (Priority: P2)

A user configures a channel with LSM v2 modulation, saves the playlist, and later reloads it. The modulation setting is preserved correctly.

**Why this priority**: Without persistence, the user would need to reconfigure the modulation every time the application starts.

**Independent Test**: Configure a channel with LSM v2, save the playlist, restart the application, verify the channel still uses LSM v2.

**Acceptance Scenarios**:

1. **Given** a channel configured with LSM v2 modulation, **When** the playlist is saved and reloaded, **Then** the channel retains the LSM v2 modulation setting
2. **Given** an older playlist file without LSM v2 entries, **When** the playlist is loaded, **Then** existing CQPSK channels default to the original LSM (no migration needed)

---

### Edge Cases

- What happens when the signal is very weak and carrier detection is ambiguous? The v2 demodulator should behave no worse than the current LSM in marginal signal conditions.
- How does the demodulator handle simulcast interference on conventional channels? Multipath may cause symbol smearing; the v2 demodulator should not over-correct PLL or timing in response.
- What happens if the user has a trunked CQPSK channel configured with LSM v2? It should still work (continuous carrier means the cold-start reset triggers rarely or never).
- What happens when transmission gaps are very short (< 50ms, e.g., rapid talk-permit-tone → voice)? The silence detector should not trigger a full reset on brief carrier drops within a logical transmission.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST add a `CQPSK_V2` entry to the `Modulation` enum with display label "Conventional (LSM v2)"
- **FR-002**: System MUST create a new `P25P1DemodulatorLSMv2` class that implements improved cold-start handling for CQPSK demodulation
- **FR-003**: The v2 demodulator MUST detect transmission boundaries (carrier-on after silence) and reset differential state, PLL, and gain to cold-start values
- **FR-004**: The v2 demodulator MUST use adaptive PLL gain: higher gain during acquisition (first ~50 symbols after reset), lower gain during steady-state tracking
- **FR-005**: The v2 demodulator MUST initialize `previousSymbolI/Q` to zero (not 0.7f) on cold start, and skip the first differential demodulation result until a valid previous symbol is established
- **FR-006**: The v2 demodulator MUST use faster AGC convergence during cold-start (e.g., 0.2 factor for first 20 symbols, then taper to 0.05)
- **FR-007**: The v2 demodulator MUST suppress Gardner TED output for the first 2 symbol periods after a reset, using nominal `samplesPerSymbol` spacing until valid previous/current symbol pairs are available
- **FR-008**: System MUST create a new `P25P1DecoderLSMv2` class that wires the v2 demodulator into the decoder pipeline
- **FR-009**: System MUST update `DecoderFactory` to instantiate `P25P1DecoderLSMv2` when modulation is `CQPSK_V2`
- **FR-010**: System MUST update the P25 Phase 1 configuration editor UI to include "Conventional (LSM v2)" as a third modulation option
- **FR-011**: The `DecodeConfigP25Phase1` Jackson serialization MUST correctly persist and restore the `CQPSK_V2` modulation value
- **FR-012**: The v2 demodulator MUST detect silence (all-zero or below-threshold samples for >= 200ms) as a transmission boundary trigger

### Key Entities

- **Modulation Enum**: Extended with `CQPSK_V2("Conventional (LSM v2)")` alongside existing `C4FM` and `CQPSK`
- **P25P1DemodulatorLSMv2**: New demodulator class with cold-start improvements. Same I/Q input interface as existing LSM demodulator.
- **P25P1DecoderLSMv2**: New decoder class that wires the v2 demodulator, inheriting the same structure as `P25P1DecoderLSM`
- **Transmission Boundary Detector**: Logic within the v2 demodulator that monitors incoming sample energy and triggers state reset when a new transmission begins after silence

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: LSM v2 modulation option appears in the UI and persists across save/load cycles
- **SC-002**: On a conventional CQPSK channel, LSM v2 decodes the first data unit of at least 80% of transmissions where the existing LSM misses it (measurable by comparing decoded message counts over a test period)
- **SC-003**: On a trunked/simulcast CQPSK channel, LSM v2 performs no worse than the existing LSM (decode rate within 5%)
- **SC-004**: The v2 demodulator achieves sync within the first 2 data units (960 symbols / 200ms) of a new transmission from cold start
- **SC-005**: Existing channels configured with `CQPSK` modulation continue to use the original LSM demodulator without any behavioral change (full backward compatibility)
