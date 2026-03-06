# Implementation Plan: Decode Quality Scoring Tool

## Architecture

**Three-layer design**: Shell orchestrator → Java decode pipeline → Python audio analysis

### Layer 1: Shell Orchestrator (`tools/score-decode-quality.sh`)
- Single entry point for all scoring operations
- Parses arguments: `--control <dir>`, `--test <dir>`, `--control-ref <commit>`, `--samples <dir>`, `--playlist <xml>`, `--mode quick|full`, `--jmbe <jar>`
- If `--control-ref` given, creates temporary git worktree, builds it
- Resolves playlist channels → baseband file mappings
- Orchestrates Java decode runs from both source trees
- Invokes Python audio scorer on decode outputs
- Generates final comparison report

### Layer 2: Java Decode Pipeline (existing infrastructure + new Gradle task)
- New `DecodeQualityTest.java` — unified decode test that:
  - Accepts baseband file, playlist XML, output directory
  - Reads playlist to determine decoder type and NAC per frequency
  - Runs the matched decoder (C4FM, CQPSK, CQPSK_V2)
  - Collects LDU count, valid messages, sync metrics
  - In full mode: decodes through JMBE → outputs MP3 audio files
  - Writes metrics JSON to output directory for Python consumption
- New Gradle task `runDecodeScore` that invokes this test

### Layer 3: Python Audio Analysis (`tools/audio_scorer.py`)
- Reads MP3 audio files from decode output directories
- Computes per-file metrics:
  - Total audio duration (seconds)
  - Segment count and average segment length
  - Dispatch tone detection (FFT-based, for FD channels)
  - Distortion event detection (spectral anomaly + silence gap detection)
  - Speech-to-text word count (via whisper if available)
  - Signal-to-decode ratio (from baseband energy analysis)
- Writes scored metrics JSON

### Report Generator (Python, part of audio_scorer.py or separate)
- Reads metrics JSON from control and test runs
- Produces text comparison table with ↑/↓ indicators
- Groups by channel, by modulation type, and aggregate

## Key Design Decisions

1. **Java for decode, Python for audio analysis** — Java reuses the existing decode infrastructure directly; Python has superior audio analysis libraries (numpy, scipy, whisper)
2. **JSON intermediate format** — Java writes decode metrics as JSON, Python reads them. Clean separation between layers.
3. **Gradle from each source tree** — Run `./gradlew runDecodeScore` from both control and test directories. This ensures each build uses its own compiled decoder code.
4. **Playlist-driven channel resolution** — Parse the playlist XML to map frequency → (decoder, NAC, channel name, preferred tuner). No manual decoder selection.

## Dependencies to Install

- `pip install openai-whisper` (or `pip install faster-whisper`) — for speech-to-text
- `pip install scipy` — for FFT-based tone/distortion detection
- `numpy` — already available
- `ffmpeg` — already available (needed by whisper for audio conversion)
