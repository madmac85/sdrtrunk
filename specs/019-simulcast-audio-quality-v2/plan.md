# Implementation Plan: Simulcast Audio Quality Improvements v2

**Branch**: `019-simulcast-audio-quality-v2` | **Date**: 2026-03-09 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/019-simulcast-audio-quality-v2/spec.md`

## Summary

Three improvements to simulcast decode quality: (1) a fast silence-detection metric integrated into the test harness for rapid A/B comparison, (2) training-assisted LMS equalization using known sync/NID symbols — the approach used by physical P25 radios, and (3) decision-directed switching after CMA convergence to correct phase errors.

## Technical Context

**Language/Version**: Java 25+ (OpenJDK with `--enable-preview`, `jdk.incubator.vector`)
**Primary Dependencies**: JMBE 1.0.9 (IMBE codec), CMAEqualizer (existing), P25P1DecoderLSMv2 (existing)
**Storage**: File-based (baseband WAV samples, MP3 audio output, JSON metrics)
**Testing**: JUnit 5 + manual decode quality testing via `runDecodeScore` Gradle task
**Target Platform**: Linux/Windows desktop (SDR receiver application)
**Project Type**: Single Java project (Gradle)
**Performance Goals**: Silence detection <1s per file; equalization must not add measurable latency to real-time decode (~25 kHz sample rate)
**Constraints**: Must not regress C4FM channels; must not regress ROC W (moderate simulcast); real-time processing at 4800 symbols/sec
**Scale/Scope**: 3 modified files for silence detection, 2-3 modified files for equalization, ~500 lines of new code

## Constitution Check

*No project constitution configured. Proceeding with standard engineering practices.*

## Project Structure

### Documentation (this feature)

```text
specs/019-simulcast-audio-quality-v2/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
src/main/java/io/github/dsheirer/
├── dsp/filter/equalizer/
│   └── CMAEqualizer.java           # MODIFY: Add LMS training mode + DD switching
├── module/decode/p25/phase1/
│   ├── P25P1DecoderLSMv2.java       # MODIFY: Wire training signals to equalizer
│   ├── P25P1MessageFramer.java      # MODIFY: Expose sync/NID events for training
│   └── P25P1DemodulatorLSMv2.java   # MODIFY: Provide symbol decisions for DD mode

src/test/java/io/github/dsheirer/
└── module/decode/p25/phase1/
    └── DecodeQualityTest.java       # MODIFY: Add silence detection metric
```

**Structure Decision**: All changes are modifications to existing files in the established project structure. No new files needed — the CMAEqualizer already has the tap/buffer infrastructure that LMS training and DD switching will extend.

## Design

### Part 1: Silence Detection Metric

**Approach**: Post-decode RMS analysis of audio float[] buffers in DecodeQualityTest.

The test harness already produces `float[160]` audio buffers per IMBE frame (8 kHz, 20ms per frame). Silence detection scans these buffers:
- Compute RMS per frame (160 samples)
- Frame is "silent" if RMS < threshold (e.g., 0.01 — well below ambient noise floor)
- Consecutive silent frames form a "silence region"
- Minimum region duration: 100ms (5 frames) to exclude natural inter-word pauses
- Report: total silence seconds, silence percentage, silence region count

**Integration**: Add to `decodeAudio()` return value and JSON metrics output. No changes to production code.

### Part 2: Training-Assisted LMS Equalization

**Approach**: Extend CMAEqualizer with a training mode that activates during known symbol sequences.

**Signal flow change**:
```
Current:  samples → CMA(blind) → demodulator → framer
Proposed: samples → CMA/LMS(hybrid) → demodulator → framer
                        ↑ training feedback ↑
                    framer reports sync/NID position
```

**Training sequence**: The P25 sync pattern is 24 dibits of known phase (only ±3π/4). When the framer detects sync at dibit position N, it means the equalizer processed the sync symbols at sample positions [N-24, N) × samplesPerSymbol. The equalizer can retroactively apply LMS corrections using stored sample history, or prospectively apply LMS during the NID field (32 dibits, partially predictable from configured NAC).

**Practical design**: Rather than retroactive correction (complex, requires re-processing), use a **prospective training approach**:
1. When framer detects sync → notify equalizer "training window opening"
2. During NID field: if NAC is configured, the NAC portion (12 bits = 6 dibits) is known, providing 6 known symbols for LMS training
3. LMS update: `w[k] -= mu_lms * conj(x[n-k]) * (y - d)` where d = known symbol value
4. After NID: revert to CMA mode for voice data

**Step sizes**: LMS mu_train = 0.05-0.1 (aggressive, known reference). CMA mu_track = 0.001 (conservative, blind).

### Part 3: Decision-Directed (DD) Switching

**Approach**: After CMA converges, use hard symbol decisions as reference instead of constant modulus.

**DD update**: `w[k] -= mu_dd * conj(x[n-k]) * (y - d_hat)` where d_hat = nearest constellation point to y.

**Convergence detection**: Monitor EMA of |y|² variance over last 100 symbols. When variance < threshold, CMA has converged and constellation is roughly correct → switch to DD.

**Divergence fallback**: If DD error metric exceeds CMA error metric for 50 consecutive symbols → revert to CMA.

**Constellation**: pi/4 DQPSK has 4 points at phases π/4, 3π/4, -π/4, -3π/4, all at unit magnitude. Nearest-point decision uses quadrant of atan2(Q, I).

## Complexity Tracking

No constitutional violations to track.
