# Tasks: Decode Quality Scoring Tool

## Phase 1: Foundation

- [x] T001 Create `DecodeQualityTest.java` — unified decode test that reads playlist XML, resolves decoder per frequency, runs decode, outputs metrics JSON + audio files
- [x] T002 Add `runDecodeScore` Gradle task in build.gradle
- [x] T003 Create `tools/score-decode-quality.sh` — shell orchestrator entry point
- [x] T004 Create `tools/audio_scorer.py` — Python audio analysis and report generation

## Phase 2: Quick Mode (LDU-only)

- [x] T005 Implement playlist XML parsing in DecodeQualityTest (frequency → channel config)
- [x] T006 Implement decode pipeline execution with metrics JSON output
- [x] T007 Implement quick mode in shell script (run decode from both trees, compare LDU counts)
- [x] T008 Test quick mode with ROC W and Derry FD samples

## Phase 3: Full Mode (Audio Quality)

- [ ] T009 Install Python dependencies (whisper, scipy)
- [x] T010 Implement audio duration and segment counting in audio_scorer.py
- [x] T011 Implement dispatch tone detection (FFT-based)
- [x] T012 Implement distortion event detection
- [x] T013 Implement speech-to-text word counting
- [x] T014 Implement signal-to-decode ratio computation
- [ ] T015 Test full mode end-to-end

## Phase 4: Reporting

- [x] T016 Implement comparison report generator (text table with ↑/↓ indicators)
- [x] T017 Add per-channel and per-modulation grouping
- [x] T018 Add preferred tuner display from playlist config
- [x] T019 Final end-to-end test across full sample corpus
