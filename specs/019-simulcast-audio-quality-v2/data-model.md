# Data Model: Simulcast Audio Quality Improvements v2

## Entities

### SilenceRegion
Represents a contiguous span of decode-failure silence within an audio segment.

| Field | Type | Description |
|-------|------|-------------|
| startTimeMs | double | Start time within the audio segment (milliseconds) |
| endTimeMs | double | End time within the audio segment (milliseconds) |
| durationMs | double | Duration of silence (endTimeMs - startTimeMs) |
| avgRms | double | Average RMS amplitude during the region |

### SilenceMetrics
Aggregate silence statistics for a decoded audio file.

| Field | Type | Description |
|-------|------|-------------|
| totalSilenceSeconds | double | Total seconds of decode-failure silence |
| silencePercentage | double | Silence as percentage of total audio duration |
| silenceRegionCount | int | Number of distinct silence regions |
| totalAudioSeconds | double | Total audio duration analyzed |
| regions | List\<SilenceRegion\> | Individual silence regions (for diagnostics) |

### EqualizerMode (enum)
Operating mode of the hybrid CMA/LMS/DD equalizer.

| Value | Description |
|-------|-------------|
| CMA | Blind constant-modulus adaptation (default) |
| LMS_TRAINING | Supervised LMS using known symbols (sync/NID) |
| DECISION_DIRECTED | Using hard symbol decisions as reference |

### ConvergenceState
Tracks equalizer convergence for DD mode switching.

| Field | Type | Description |
|-------|------|-------------|
| modulusVarianceEma | double | EMA of (|y|² - 1)² |
| isConverged | boolean | True when EMA < convergence threshold |
| ddErrorEma | double | EMA of DD error during DD mode |
| consecutiveDivergent | int | Count of consecutive high-error symbols in DD mode |

## Relationships

- `DecodeQualityTest` produces `SilenceMetrics` per audio file
- `CMAEqualizer` maintains `ConvergenceState` and current `EqualizerMode`
- `P25P1MessageFramer` notifies `CMAEqualizer` of training window positions
- `P25P1DemodulatorLSMv2` provides hard symbol decisions for DD mode

## State Transitions

### Equalizer Mode Transitions

```
                    ┌─────────────────────┐
                    │                     │
    cold start ──→ CMA ──(converged)──→ DD
                    ↑                     │
                    │    (diverged)        │
                    └─────────────────────┘
                    │
                    │ (sync detected + NAC configured)
                    ↓
                LMS_TRAINING ──(NID complete)──→ CMA or DD
```

- **CMA → DD**: modulusVarianceEma < 0.1 (converged)
- **DD → CMA**: consecutiveDivergent > 50 (diverged)
- **CMA → LMS_TRAINING**: framer signals training window, NAC configured
- **LMS_TRAINING → CMA/DD**: NID field complete (32 dibits processed)
- **Any → CMA**: cold-start reset (transmission boundary)
