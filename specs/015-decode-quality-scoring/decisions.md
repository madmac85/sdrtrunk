# Decisions: Decode Quality Scoring Tool

## 2026-03-06 - Architecture: Shell + Java + Python
**Context**: Need a single-command tool that decodes baseband and scores audio quality
**Decision**: Shell script orchestrates Java decode pipeline (reusing existing infrastructure) and Python audio analysis
**Rationale**: Java reuses the proven decode pipeline; Python has superior audio/ML libraries (whisper, scipy, numpy)
**Alternatives Considered**: Pure Java (no good STT/audio analysis libs), Pure Python (would need to reimplement entire P25 decode stack)

## 2026-03-06 - Inter-process communication via JSON
**Context**: Java decode and Python analysis run as separate processes
**Decision**: Java writes metrics as JSON files to output directory; Python reads them
**Rationale**: Clean separation, debuggable intermediate state, language-agnostic
**Alternatives Considered**: Stdout piping (fragile), shared database (overkill)

## 2026-03-06 - Whisper for STT
**Context**: Need local speech-to-text for word counting
**Decision**: Use OpenAI Whisper (base model) for offline transcription
**Rationale**: Best accuracy for English speech, runs fully offline, no API keys needed
**Alternatives Considered**: Vosk (lower accuracy), Google STT (requires API key/internet)

## 2026-03-06 - DecoderWrapper interface pattern
**Context**: P25P1DecoderC4FM, P25P1DecoderLSM, P25P1DecoderLSMv2 share FeedbackDecoder base but `receive(ComplexSamples)` and `setSampleRate()` are on concrete classes only
**Decision**: Create `DecoderWrapper` interface with anonymous implementations wrapping each concrete decoder type
**Rationale**: Avoids unsafe casts, cleanly abstracts decoder differences, mirrors existing test patterns

## 2026-03-06 - TestComplexWaveSource for file reading
**Context**: ComplexWaveSource has no `hasNext()` method; uses `next(int)` which throws IOException at EOF
**Decision**: Use TestComplexWaveSource (existing test utility) with `while(source.next(2048)) {}` pattern
**Rationale**: TestComplexWaveSource provides position-based timestamps (not wall-clock), boolean-returning `next()`, and is already proven in existing tests

## 2026-03-06 - RMS energy threshold for signal detection
**Context**: Need to determine when a signal is present on the channel for decode ratio computation
**Decision**: Compute per-buffer RMS energy from I/Q samples, threshold at 0.01 (empirical: noise floor ~0.002, signal ~0.02+)
**Rationale**: Simple, fast, no additional dependencies. Threshold is conservative to avoid false positives from noise
