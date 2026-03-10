# Tasks: Simulcast Audio Quality Improvements v2

**Input**: Design documents from `/specs/019-simulcast-audio-quality-v2/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Not explicitly requested. Test validation is done via the decode quality test harness (`runDecodeScore`).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: Establish baselines and verify current state before making changes

- [X] T001 Run current decode quality baselines for all 4 LSM v2 channels (ROC W, ROC E, LFD, WPD) with `./gradlew runDecodeScore` and record LDU counts, audio seconds, and STT word counts as pre-change reference
- [X] T002 Run current decode quality baseline for C4FM channels (Derry FD) to establish regression guard

**Checkpoint**: Baselines recorded for all channels

---

## Phase 2: Foundational

**Purpose**: No foundational/blocking infrastructure changes needed — all work extends existing files

**Checkpoint**: Foundation ready (no blocking work needed)

---

## Phase 3: User Story 1 - Silence Detection Metric (Priority: P1)

**Goal**: Fast, deterministic audio quality metric that measures decode-failure silence in decoded audio, integrated into the test harness JSON output.

**Independent Test**: Run `./gradlew runDecodeScore -Pmode=full` on any baseband sample and verify `silence_seconds`, `silence_percentage`, and `silence_region_count` appear in `metrics.json`. Verify ROC W shows low silence (<10%) and LFD shows high silence.

### Implementation for User Story 1

- [X] T003 [US1] Add silence detection analysis to the `decodeAudio()` method in `src/test/java/io/github/dsheirer/module/decode/p25/phase1/DecodeQualityTest.java`: After collecting all audio buffers, scan float[160] frames computing per-frame RMS. Mark frames with RMS < 0.01 as silent. Group consecutive silent frames into regions. Only count regions >= 5 frames (100ms) as decode-failure silence. Track total silence seconds, silence percentage, and region count.

- [X] T004 [US1] Add silence metrics to the JSON output in `DecodeQualityTest.java`: Add `silence_seconds`, `silence_percentage`, and `silence_region_count` fields to the per-file metrics JSON object written by the `writeMetrics()` method.

- [X] T005 [US1] Add `--silence-threshold` command-line option to `DecodeQualityTest.java` (default 0.01) and `--silence-min-ms` option (default 100) for tuning the silence detection parameters. Wire through `build.gradle` as `-PsilenceThreshold` and `-PsilenceMinMs` Gradle properties.

- [X] T006 [US1] Run silence detection on all existing baselines (ROC W configs A-D from spec 018 investigation, LFD, WPD, Derry FD) and verify: ROC W config D (best) shows <10% silence, LFD current config shows high silence %, Derry FD (C4FM) shows low silence. Print silence metrics in console output alongside LDU counts.

**Checkpoint**: Silence detection metric fully functional. Can rapidly compare decode configurations without STT.

---

## Phase 4: User Story 2 - Training-Assisted LMS Equalization (Priority: P2)

**Goal**: Extend the CMA equalizer with a supervised LMS training mode that uses known NID symbols (when NAC is configured) to achieve faster, more accurate equalization — matching the approach used by physical P25 radios.

**Independent Test**: Run LFD and WPD samples with LMS training enabled vs disabled, compare silence percentage and LDU counts. Verify ROC W shows no regression.

### Implementation for User Story 2

- [X] T007 [US2] Add training mode infrastructure to `src/main/java/io/github/dsheirer/dsp/filter/equalizer/CMAEqualizer.java`: Add an `EqualizerMode` enum (CMA, LMS_TRAINING, DECISION_DIRECTED). Add `mMode` field, `mLmsMu` step size field (default 0.05, configurable via `eq.lms.mu` system property). Add `setTrainingSymbol(float refI, float refQ)` method that switches to LMS mode and stores the reference symbol. Add `clearTraining()` method that reverts to CMA mode.

- [X] T008 [US2] Implement LMS update logic in `CMAEqualizer.equalize()` in `src/main/java/io/github/dsheirer/dsp/filter/equalizer/CMAEqualizer.java`: When `mMode == LMS_TRAINING` and a reference symbol is set, compute LMS error as `e = y - d` (where d = reference) instead of CMA error `e = y * (|y|² - R)`. Apply tap update with `mLmsMu` instead of `mMu`. After processing the training sample, clear the reference (single-shot per symbol).

- [X] T009 [US2] Add training signal notification to `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`: After sync is detected and NID decoding begins, if `mConfiguredNAC > 0`, compute the expected NID dibits for the known NAC portion (first 6 dibits = 12 NAC bits). Expose a method `getTrainingDibits()` that returns the expected dibit sequence, and add a listener interface `ITrainingSequenceListener` with method `trainingDibitsAvailable(Dibit[], int)`.

- [X] T010 [US2] Wire training signal from framer to equalizer in `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderLSMv2.java`: Implement the `ITrainingSequenceListener` interface. When the framer notifies of a training symbol, call `mEqualizer.setTrainingSymbol(refI, refQ)` so the next equalized sample uses LMS instead of CMA. Register the decoder as a training listener on the framer.

- [X] T011 [US2] Add system property `eq.lms.enable` (default true for CQPSK_V2 with NAC, false otherwise) to control training-assisted LMS in `P25P1DecoderLSMv2.java`. Ensure C4FM decoder is completely unaffected.

- [X] T012 [US2] Run A/B comparison on all LSM v2 channels: LMS enabled vs disabled. Use silence detection metric (US1) + LDU counts. Record results. Verify: LFD and WPD show reduced silence %, ROC W shows no regression, Derry FD (C4FM) shows zero change.

**Checkpoint**: Training-assisted LMS equalization functional. Severe simulcast channels should show measurable improvement.

---

## Phase 5: User Story 3 - Decision-Directed Switching (Priority: P3)

**Goal**: After CMA converges, switch to decision-directed mode using pi/4 DQPSK constellation points as references to correct phase errors that CMA is blind to.

**Independent Test**: Run ROC W and LFD with DD enabled vs disabled, compare silence % and STT words. Verify no regression on any channel.

### Implementation for User Story 3

- [X] T013 [US3] Add convergence detection to `src/main/java/io/github/dsheirer/dsp/filter/equalizer/CMAEqualizer.java`: Add `mModulusVarianceEma` field (EMA of (|y|² - 1)², alpha=0.01). Update per sample in `equalize()`. Add `isConverged()` method returning true when EMA < 0.1. Add `mDdMu` step size field (default 0.01, configurable via `eq.dd.mu` system property).

- [X] T014 [US3] Implement decision-directed update in `CMAEqualizer.equalize()` in `src/main/java/io/github/dsheirer/dsp/filter/equalizer/CMAEqualizer.java`: When `mMode == CMA` and `isConverged()` is true, auto-switch to `DECISION_DIRECTED` mode. In DD mode, compute nearest pi/4 DQPSK constellation point (quadrant test on I/Q), use it as reference: `e = y - d_hat`. Apply tap update with `mDdMu`. Track DD error EMA; if DD error exceeds CMA-equivalent error for 50 consecutive samples, fall back to CMA mode.

- [X] T015 [US3] Add `eq.dd.enable` system property (default true) in `CMAEqualizer.java` to control DD switching. When disabled, equalizer stays in CMA mode after convergence. Ensure DD mode resets to CMA on `reset()` (cold-start).

- [X] T016 [US3] Run A/B comparison on all channels: DD enabled vs disabled (with LMS training also enabled). Use silence detection + STT. Verify no regression on ROC W, improvement on LFD/WPD, zero change on C4FM.

**Checkpoint**: Full hybrid equalizer (CMA + LMS training + DD) functional and validated.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and regression testing

- [X] T017 Run full regression suite: all 4 LSM v2 channels + 2 C4FM channels with final configuration. Compare against T001/T002 baselines. Produce comparison table with LDU counts, silence %, STT words.
- [X] T018 Update `CLAUDE.md` memory with new system properties (`eq.lms.mu`, `eq.lms.enable`, `eq.dd.mu`, `eq.dd.enable`), silence detection parameters, and test results.
- [ ] T019 Commit final changes with descriptive message.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — establishes baselines
- **Foundational (Phase 2)**: N/A — no blocking infrastructure
- **User Story 1 (Phase 3)**: No dependencies on other stories — test harness only
- **User Story 2 (Phase 4)**: Depends on US1 for silence detection metric (used in T012 validation)
- **User Story 3 (Phase 5)**: Depends on US2 (DD extends the same equalizer infrastructure)
- **Polish (Phase 6)**: Depends on all stories complete

### User Story Dependencies

- **User Story 1 (P1)**: Independent — modifies only `DecodeQualityTest.java` and `build.gradle`
- **User Story 2 (P2)**: Uses US1 silence metric for validation but is independently implementable
- **User Story 3 (P3)**: Extends US2's equalizer mode infrastructure; should be implemented after US2

### Within Each User Story

- Implementation tasks are sequential (each builds on prior)
- Validation task (T006/T012/T016) must be last in each phase

### Parallel Opportunities

- T001 and T002 (baselines) can run in parallel
- T003, T004, T005 (silence detection implementation) are sequential within US1
- T007 and T009 (equalizer + framer changes) can be developed in parallel, then T010 wires them together
- T013, T014, T015 (DD implementation) are sequential within US3

---

## Parallel Example: User Story 2

```bash
# These can be developed in parallel (different files):
Task T007: "CMAEqualizer.java - Add training mode infrastructure"
Task T009: "P25P1MessageFramer.java - Add training signal notification"

# Then wire together (depends on both above):
Task T010: "P25P1DecoderLSMv2.java - Wire training signal from framer to equalizer"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Baselines (T001-T002)
2. Complete Phase 3: Silence Detection (T003-T006)
3. **STOP and VALIDATE**: Silence metric works, correlates with quality
4. Use silence metric to rapidly evaluate current config on all channels

### Incremental Delivery

1. Silence Detection (US1) → Immediate measurement capability
2. Training-Assisted LMS (US2) → Core equalization improvement
3. Decision-Directed (US3) → Phase error correction refinement
4. Each story validated independently before proceeding

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Silence detection (US1) is test-harness only — no production code changes
- US2 and US3 modify production DSP code — must verify no C4FM regression
- Commit after each story checkpoint
- All validation uses the deterministic silence metric (fast) plus spot-check STT (slow, for key configs)
