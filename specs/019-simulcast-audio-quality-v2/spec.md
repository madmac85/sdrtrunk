# Feature Specification: Simulcast Audio Quality Improvements v2

**Feature Branch**: `019-simulcast-audio-quality-v2`
**Created**: 2026-03-09
**Status**: Draft
**Input**: User description: "ROC W working well, WPD and LFD still badly distorted. System inserts silence during decode failures while keeping transmissions open. Add silence detection as a fast audio quality metric. Implement recommended improvements from previous specs including training-assisted equalization similar to physical radios."

## Context

Following specs 017 and 018, the CMA equalizer and BCH threshold controls significantly improved ROC W decode quality (+69% LDUs, +30% STT words). However, Windham PD and Londonderry FD remain badly distorted — initial impressions suggest they may be worse than before. The CMA equalizer appears to be keeping transmissions open longer (good) but inserting extended silence when decode fails (bad for severe simulcast). This creates a pattern of near-perfect silence within active transmissions, visible on waveforms.

Two improvements are needed:
1. A fast silence-detection metric to measure decode failure severity without slow STT transcription
2. Advanced equalization techniques identified in spec 018 research — specifically training-assisted LMS (what physical P25 radios do) and decision-directed switching — to improve the actual decode quality for severe simulcast channels

### Previous Spec Findings (Spec 018 Research)

The following improvement candidates were identified but not implemented:

| Candidate | Expected Impact | Complexity | Description |
|-----------|-----------------|------------|-------------|
| Training-Assisted LMS | HIGH | MEDIUM | Use known sync + predicted NID as training sequence with large mu, switch to CMA for data. This is what commercial P25 radios do. |
| CMA-to-Decision-Directed Switching | HIGH | MEDIUM | After CMA converges, switch to decision-directed mode using pi/4 DQPSK constellation. Corrects phase errors CMA is blind to. |
| Dual-Path Selection | MEDIUM | LOW | Run two parallel equalization paths (conservative + aggressive), select best per-frame by IMBE FEC error count. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Silence Detection Metric (Priority: P1)

As a developer tuning simulcast decode quality, I want a fast metric that measures how much total silence (decode failure) exists in decoded audio, so I can compare configurations without waiting for slow Whisper STT transcription.

The current test harness produces MP3 audio segments. Within those segments, extended periods of near-zero amplitude indicate the decoder kept the transmission open but failed to produce actual audio — a decode failure masked as silence. This is distinct from natural pauses or open-mic silence, which has background noise.

The metric should analyze decoded audio and report total seconds of "dead silence" (amplitude near zero for a sustained period), the percentage of audio that is silent, and the number of distinct silent regions.

**Why this priority**: This is the fastest path to a measurable quality metric. STT takes minutes per run; silence detection takes seconds. It enables rapid A/B comparison of equalization configurations across all channels.

**Independent Test**: Can be fully tested by running the existing test harness on any baseband sample, then analyzing the output audio for silence. Delivers immediate value as a regression metric.

**Acceptance Scenarios**:

1. **Given** a decoded audio file with known silence gaps, **When** the silence detector analyzes it, **Then** it reports total silence seconds, silence percentage, and count of silent regions within 1 second per file
2. **Given** a high-quality decode (ROC W with CMA), **When** analyzed, **Then** silence percentage is low (under 10%), confirming good decode quality
3. **Given** a heavily distorted decode (LFD current config), **When** analyzed, **Then** silence percentage is high, quantifying the decode failure rate
4. **Given** natural speech pauses (open mic, background noise), **When** analyzed, **Then** they are NOT counted as decode-failure silence because they have non-zero ambient noise amplitude

---

### User Story 2 - Training-Assisted LMS Equalization (Priority: P2)

As a user monitoring severe simulcast channels (LFD, WPD), I want the decoder to use the known sync pattern and predicted NID as a training sequence for the equalizer — the same approach used by physical P25 radios — so that the equalizer converges faster and more accurately on each frame, reducing distortion.

Current CMA equalization is "blind" — it doesn't use any known reference symbols. Physical P25 radios exploit the fact that every frame starts with a known 24-symbol sync pattern, followed by a NID whose NAC portion is known (when configured). This gives 24-56 known symbols per frame that can be used for supervised (LMS) equalization with aggressive step sizes (mu=0.05-0.1), achieving near-instant convergence. The equalizer then switches to conservative CMA for the unknown data symbols.

**Why this priority**: This is the highest-impact improvement identified in spec 018 research. It directly addresses WHY CMA struggles on severe simulcast: blind convergence is too slow for rapidly-changing multipath, but training-assisted convergence using known symbols can track fast channel changes.

**Independent Test**: Can be tested by running LFD and WPD samples through the decoder with training-assisted LMS vs current CMA-only, comparing LDU counts, silence percentage (Story 1), and STT word counts.

**Acceptance Scenarios**:

1. **Given** a severe simulcast channel (LFD) with configured NAC, **When** decoded with training-assisted LMS, **Then** silence percentage decreases compared to CMA-only
2. **Given** a moderate simulcast channel (ROC W) with configured NAC, **When** decoded with training-assisted LMS, **Then** quality is at least as good as current CMA-only (no regression)
3. **Given** a channel without configured NAC, **When** decoded, **Then** the system falls back to CMA-only behavior (training requires known NAC for NID prediction)

---

### User Story 3 - Decision-Directed Switching (Priority: P3)

As a user monitoring simulcast channels, I want the equalizer to switch from CMA to decision-directed mode after convergence, so that phase errors that CMA cannot correct are handled, reducing audio distortion.

CMA optimizes for constant modulus (amplitude) but is blind to phase errors. After CMA converges and the constellation is roughly correct, switching to decision-directed equalization uses the known pi/4 DQPSK constellation points as references to correct residual phase errors. This directly addresses why aggressive CMA can improve decode counts while still producing distorted audio.

**Why this priority**: This is the second highest-impact improvement from spec 018 research. It addresses a fundamental limitation of CMA — phase blindness — and complements the training-assisted LMS (Story 2). However, it requires CMA to have approximately converged first, making it dependent on Story 2's convergence improvements.

**Independent Test**: Can be tested by comparing audio quality metrics (silence %, STT words) with CMA-only vs CMA+DD switching on the same samples.

**Acceptance Scenarios**:

1. **Given** a converged CMA equalizer on a simulcast channel, **When** decision-directed mode activates, **Then** constellation phase spread decreases
2. **Given** ROC W sample decoded with CMA+DD, **When** compared to CMA-only, **Then** STT word count does not decrease (no regression)
3. **Given** an unconverged or severely corrupted channel, **When** DD mode would activate prematurely, **Then** the system detects non-convergence and stays in CMA mode

---

### Edge Cases

- What happens when the entire transmission is silence (complete decode failure)? The silence detector should report 100% silence.
- What happens when audio has brief dropouts (1-2 frames) vs sustained silence (multiple seconds)? The metric should distinguish brief gaps from sustained decode failures via a minimum duration threshold.
- What happens when training-assisted LMS encounters a frame where the predicted NID is wrong (NAC mismatch due to corruption)? The LMS update should be gated on successful BCH decode of the NID.
- What happens when CMA hasn't converged and decision-directed mode is triggered? A convergence detector must prevent premature DD switching.
- What happens on C4FM (non-simulcast) channels? Training-assisted LMS and DD switching should only apply to CQPSK/LSM decoders. C4FM must be completely unaffected.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a silence detection metric that measures total seconds of near-zero amplitude audio within decoded segments
- **FR-002**: The silence detector MUST distinguish decode-failure silence (near-zero amplitude) from natural speech pauses (ambient noise present) using an amplitude threshold
- **FR-003**: The silence detector MUST report: total silence seconds, silence percentage of total audio, and count of distinct silent regions
- **FR-004**: Silence detection MUST complete in under 1 second per audio file (orders of magnitude faster than STT)
- **FR-005**: The silence metric MUST be integrated into the existing test harness output metrics
- **FR-006**: System MUST implement training-assisted LMS equalization that uses the known 24-symbol sync pattern as a training sequence with supervised learning
- **FR-007**: When NAC is configured, the training-assisted LMS MUST additionally use the predicted NID (NAC + DUID) as training symbols, extending the training sequence
- **FR-008**: LMS training MUST use an aggressive step size during known symbols and switch to conservative CMA during unknown data symbols within the same frame
- **FR-009**: Training-assisted LMS MUST only activate for CQPSK/LSM v2 decoders; C4FM channels MUST be unaffected
- **FR-010**: System MUST implement decision-directed equalization that activates after CMA convergence, using pi/4 DQPSK constellation points as references
- **FR-011**: Decision-directed mode MUST include a convergence detector that prevents premature activation
- **FR-012**: If decision-directed mode detects divergence (increasing error), it MUST fall back to CMA mode
- **FR-013**: All equalization improvements MUST be tested against existing gold-standard baselines (ROC W, LFD, Derry FD) to verify no regression

### Key Entities

- **Silence Region**: A contiguous span of audio where RMS amplitude falls below a threshold for a minimum duration. Key attributes: start time, end time, duration, average amplitude.
- **Training Sequence**: Known symbols within a P25 frame (sync pattern + optionally NID with known NAC) used as reference for supervised equalization. Length: 24-56 symbols depending on NAC availability.
- **Convergence State**: The equalizer's current operating mode (training/CMA/decision-directed) and convergence metric used to determine mode transitions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Silence detection metric runs in under 1 second per audio file and produces consistent, deterministic results across runs (unlike STT which has 15-20% variance)
- **SC-002**: Silence percentage correlates with perceived audio quality: ROC W (good quality) shows under 10% silence; LFD current config (poor quality) shows measurably higher silence
- **SC-003**: Training-assisted LMS reduces silence percentage on LFD by at least 20% compared to CMA-only baseline
- **SC-004**: Training-assisted LMS maintains or improves STT word count on ROC W compared to current CMA config (no regression on moderate simulcast)
- **SC-005**: Combined improvements (LMS + DD) reduce silence percentage on WPD to under 30%
- **SC-006**: C4FM channels (Derry FD, Windham FD Dig) show zero change in LDU counts and audio quality (complete isolation)
- **SC-007**: All quality metrics (silence %, STT words, LDU count) are available in the test harness output for automated comparison

## Assumptions

- The near-zero amplitude silence pattern observed in current decoded audio is caused by the decoder keeping transmissions open (via holdover or CMA-extended sync) while failing to produce decodable voice frames, resulting in silence or codec reset frames.
- An RMS amplitude threshold can reliably distinguish decode-failure silence from natural speech pauses, because background noise in radio transmissions produces measurable ambient amplitude even during pauses.
- The 24-symbol P25 sync pattern provides sufficient training length for LMS convergence in moderate multipath conditions. Severe multipath may additionally require the NID training symbols.
- Decision-directed equalization convergence can be detected by monitoring the mean squared error between equalized symbols and nearest constellation points.
- Silence percentage and STT word count will be correlated but not identical metrics — silence detection measures decode failures while STT measures intelligibility of successfully decoded frames.

## Dependencies

- Spec 018 CMA equalizer infrastructure (already merged to master)
- JMBE 1.0.9 codec for audio decode testing
- Whisper STT for validation comparisons (not required for silence detection itself)
- Existing test harness (`runDecodeScore`) for integration
