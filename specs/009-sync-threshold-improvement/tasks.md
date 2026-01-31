# Implementation Tasks: Sync Threshold Improvement

## Task 1: Add Initial Acquisition Constants and State to P25P1MessageFramer ✅
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`

Add:
- `SYNC_INITIAL_THRESHOLD = 44` constant
- `INITIAL_ACQUISITION_WINDOW_SYMBOLS = 480` constant (100ms)
- `mInitialAcquisitionActive` boolean state
- `mAcquisitionWindowSymbolCount` counter
- `mInitialAcquisitionSyncCount` counter for diagnostics

## Task 2: Implement Adaptive Threshold Methods in P25P1MessageFramer ✅
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`

Add methods:
- `setInitialAcquisitionActive(boolean)` - activates/deactivates mode
- `isInitialAcquisitionActive()` - query method
- `getCurrentInitialThreshold()` - returns ramped threshold (44→52→60)
- `getInitialAcquisitionSyncCount()` - counter getter

## Task 3: Modify processWithSoftSyncDetect() for Initial Acquisition ✅
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`

Modify sync detection logic to:
- Track acquisition window countdown
- Use initial acquisition threshold when active
- Increment `mInitialAcquisitionSyncCount` on sync detection

## Task 4: Update Diagnostics in P25P1MessageFramer ✅
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`

Update `getDiagnostics()` to include initial acquisition sync count in output.

## Task 5: Activate Initial Acquisition in P25P1DecoderLSMv2 ✅
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderLSMv2.java`

Modify `detectTransmissionBoundary()` to call `mMessageFramer.setInitialAcquisitionActive(true)` when transitioning from silence to signal.

## Task 6: Add Faster Fade Detection During Acquisition ✅
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderLSMv2.java`

Add:
- `ACQUISITION_FADE_WINDOW_SAMPLES = 625` constant (25ms)
- Modify fade detection to use shorter window when `mMessageFramer.isInitialAcquisitionActive()`

## Task 7: Add preambleEnergy and energyVariance to Transmission Record ✅
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/Transmission.java`

Add:
- `preambleEnergy` field
- `energyVariance` field
- `preambleRatio()` method
- `hasWeakPreamble()` method

## Task 8: Calculate Preamble Energy and Variance in TransmissionMapper ✅
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionMapper.java`

Modify signal processing to:
- Track first 100ms average energy as preamble energy
- Calculate energy variance using Welford's algorithm
- Pass new values to Transmission constructor

## Task 9: Update SignalProfile Factory Method ✅
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/SignalProfile.java`

Update `fromEnergyValues()` to accept preambleEnergy and energyVariance parameters.

## Task 10: Add Variance and Preamble Correlation to MissedTransmissionAnalyzer ✅
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/MissedTransmissionAnalyzer.java`

Update `printSignalQualityCorrelation()` to include energy variance and preamble ratio correlations.

## Task 11: Update SyncFailureInvestigator to Use Actual Metrics ✅
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/SyncFailureInvestigator.java`

Update `extractSyncFailures()` to pass actual preamble energy and variance from Transmission to SignalProfile.

## Task 12: Run Verification Tests ✅
Execute:
1. `./gradlew compileJava` - verify decoder compiles ✅
2. `./gradlew runComparison` - run LDU comparison ✅
3. `./gradlew runMissedTransmissionAnalysis` - verify metrics ✅
4. Verified preamble and variance correlations in output ✅

---

## Dependencies

```
Task 1 ──► Task 2 ──► Task 3 ──► Task 4 ──┬──► Task 5 ──► Task 6
                                          │
Task 7 ──► Task 8 ──► Task 9 ──► Task 10 ─┴──► Task 11 ──► Task 12
```

## Acceptance Criteria

- [x] Decoder compiles without errors
- [x] Initial acquisition mode activates on transmission start
- [x] Adaptive threshold ramps 44→52→60 over 100ms
- [x] Diagnostics include initial acquisition sync count
- [x] Transmission record includes preambleEnergy and energyVariance
- [x] Analysis tools report variance and preamble correlations

## Verification Results

### LDU Improvement (Single Sample File)
- LSM: 79 LDUs → v2: 118 LDUs (+39, +49.4%)
- Audio: 14.2s → 21.2s (+7.0s)
- No regressions detected

### Diagnostics Confirm Strategy Activation
- Boundary resets: 4
- Initial acquisition syncs: 9
- Fallback syncs: 22996
- Fade recovery: 24

### Signal Quality Analysis (8 Sample Files)
- Weak preamble (<70%): 109 TX, 6.2% decode rate
- Normal preamble (>=70%): 121 TX, 40.2% decode rate
- **34.0% decode rate gap** - significant weak preamble impact

- High variance (>30%): 48 TX, 2.4% decode rate
- Normal variance (<=30%): 182 TX, 13.3% decode rate
- **10.9% decode rate gap** - significant variance impact
