# Implementation Plan: Sync Threshold Improvement for P25 LSM v2

## Overview

Implement sync acquisition improvements to reduce sync failures by 36% (from 25 to 16 or fewer). The investigation (008) identified 72% of failures are WEAK_PREAMBLE (weak initial energy) and 8% are RAPID_FADE.

## Architecture

### Current Sync Detection Hierarchy

```
P25P1MessageFramer thresholds:
├── Standard (60) - Primary detection
├── Fallback (52) - With energy confirmation
├── Fade (48) - During signal fade-out
└── Hard Sync - 4-bit error tolerant pattern match
```

### New Hierarchy with Initial Acquisition

```
During first 100ms of transmission:
├── Standard (60) - Always checked first
├── Initial Acquisition (44→52→60 ramped)  ← NEW
├── Fade (48) - If fade detected
├── Fallback (52) - With energy confirmation
└── Hard Sync - Boundary recovery

After 100ms:
├── Standard (60)
├── Fade (48)
├── Fallback (52)
└── Hard Sync
```

## Implementation Strategy

### Phase 1: P25P1MessageFramer Changes

Add initial acquisition mode with adaptive threshold ramping:

| Constant | Value | Purpose |
|----------|-------|---------|
| `SYNC_INITIAL_THRESHOLD` | 44 | Lower threshold for weak preambles |
| `INITIAL_ACQUISITION_WINDOW_SYMBOLS` | 480 | 100ms at 4800 symbols/sec |

**Adaptive Ramping (3 steps in 100ms):**
- 0-33ms: Threshold 44 (initial)
- 33-67ms: Threshold 52 (fallback)
- 67-100ms: Threshold 60 (standard)

**New Methods:**
- `setInitialAcquisitionActive(boolean)` - Called by decoder on transmission start
- `isInitialAcquisitionActive()` - Query for fade detection optimization
- `getCurrentInitialThreshold()` - Returns ramped threshold based on progress
- `getInitialAcquisitionSyncCount()` - Counter for diagnostics

### Phase 2: P25P1DecoderLSMv2 Changes

Activate initial acquisition on transmission boundary detection:

```java
// In detectTransmissionBoundary(), when mInSilence transitions to false:
mMessageFramer.setInitialAcquisitionActive(true);
```

Extend fade recovery with shorter window during acquisition:
- Normal fade window: 50ms (1250 samples)
- Acquisition fade window: 25ms (625 samples) for faster response

### Phase 3: Analysis Tool Updates

**Transmission.java:**
- Add `preambleEnergy` field (average of first 100ms)
- Add `energyVariance` field (standard deviation)
- Add `preambleRatio()` and `hasWeakPreamble()` methods

**TransmissionMapper.java:**
- Calculate preamble energy during first 100ms of each signal period
- Calculate energy variance using Welford's online algorithm

**MissedTransmissionAnalyzer.java:**
- Add energy variance to correlation analysis
- Add preamble ratio to correlation analysis

**SignalProfile.java:**
- Update `fromEnergyValues()` to accept actual preamble and variance values

## Files to Modify

| File | Changes |
|------|---------|
| `P25P1MessageFramer.java` | Add initial acquisition mode, adaptive threshold, counter |
| `P25P1DecoderLSMv2.java` | Activate initial acquisition on TX start, shorter fade window |
| `Transmission.java` | Add preambleEnergy, energyVariance fields |
| `TransmissionMapper.java` | Calculate preamble energy and variance |
| `MissedTransmissionAnalyzer.java` | Add variance/preamble correlation |
| `SignalProfile.java` | Update factory method signature |
| `SyncFailureInvestigator.java` | Use actual transmission metrics |

## Verification

1. Run `./gradlew compileJava` to verify decoder changes compile
2. Run `./gradlew test --tests "*SyncFailureInvestigatorTest"` to verify analysis
3. Verify sync failure count decreases from 25 to 16 or fewer
4. Verify WEAK_PREAMBLE failures decrease by 50%
5. Verify no regression in 174 successful transmissions
6. Verify new counter appears in diagnostics output

## Risk Mitigation

- **False positives**: Initial threshold only active for 100ms, with ramping
- **Performance**: Single additional comparison per symbol during window
- **Backward compatibility**: All existing thresholds and strategies preserved
