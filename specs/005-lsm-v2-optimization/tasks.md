# Implementation Tasks: P25 LSM v2 Decoder Optimization

## Task Overview

| ID | Task | Priority | Dependencies | Status |
|----|------|----------|--------------|--------|
| T1 | Run baseline measurement | P1 | None | Complete |
| T2 | Implement adaptive sync threshold | P1 | T1 | Complete |
| T3 | Implement boundary recovery window | P1 | T2 | Complete |
| T4 | Implement TDU recovery | P1 | T3 | Complete |
| T5 | Run full regression suite | P1 | T4 | Complete |
| T6 | Generate final report | P1 | T5 | Complete |

---

## T1: Run Baseline Measurement

**Objective**: Establish baseline metrics for HDU/TDU detection rates across all 8 samples.

**Subtasks**:
1. Update `TransmissionScorer.java` to track HDU/TDU detection rates
2. Update `TransmissionScoringTest.java` to report HDU/TDU rates in summary
3. Run scoring on all 8 sample files with NAC=117
4. Document baseline results in `baseline-results.md`

**Acceptance Criteria**:
- HDU detection rate calculated per sample
- TDU detection rate calculated per sample
- Results documented with per-sample breakdown

---

## T2: Implement Adaptive Sync Threshold (Strategy 1)

**Objective**: Improve HDU detection by lowering sync threshold when signal is present but sync not detected.

**Subtasks**:
1. Add `ISignalEnergyProvider` reference to `P25P1MessageFramer`
2. Add fallback sync threshold constant (45)
3. Implement energy-aware sync threshold selection
4. Add diagnostic counter for fallback sync triggers
5. Test on sample with known HDU misses

**Code Changes**:

```java
// P25P1MessageFramer.java
private static final float SYNC_DETECTION_THRESHOLD = 60;
private static final float SYNC_FALLBACK_THRESHOLD = 45;  // NEW
private static final int FALLBACK_WINDOW_SYMBOLS = 480;   // 100ms

private ISignalEnergyProvider mEnergyProvider;           // NEW
private int mSymbolsSinceEnergyRise = 0;                 // NEW
private int mFallbackSyncCount = 0;                      // NEW diagnostic

public void setEnergyProvider(ISignalEnergyProvider provider) {
    mEnergyProvider = provider;
}

public boolean processWithSoftSyncDetect(float softSymbol, Dibit symbol) {
    boolean validNIDDetected = process(symbol);

    float syncScore = mSoftSyncDetector.process(softSymbol);

    // Primary threshold
    if(syncScore > SYNC_DETECTION_THRESHOLD) {
        syncDetected();
    }
    // Fallback threshold when signal present but no sync
    else if(syncScore > SYNC_FALLBACK_THRESHOLD &&
            mEnergyProvider != null &&
            mEnergyProvider.isSignalPresent() &&
            mSymbolsSinceEnergyRise < FALLBACK_WINDOW_SYMBOLS) {
        syncDetected();
        mFallbackSyncCount++;
    }

    return validNIDDetected;
}
```

**Acceptance Criteria**:
- Fallback sync triggers only when signal energy confirms transmission
- No false sync triggers during silence
- HDU detection rate improves by >=10%

---

## T3: Implement Boundary Recovery Window (Strategy 2)

**Objective**: Use hard sync detection in parallel with soft sync for first 100ms after boundary reset.

**Subtasks**:
1. Add `mBoundaryRecoveryActive` flag to `P25P1MessageFramer`
2. Add `setBoundaryRecoveryActive()` method called from decoder
3. Modify sync detection to use hard sync during recovery
4. Add diagnostic counter for recovery sync triggers
5. Wire decoder to framer for boundary notification

**Code Changes**:

```java
// P25P1MessageFramer.java
private static final int RECOVERY_WINDOW_SYMBOLS = 480;  // 100ms
private boolean mBoundaryRecoveryActive = false;
private int mRecoverySymbolCount = 0;
private int mRecoverySyncCount = 0;  // diagnostic

public void setBoundaryRecoveryActive(boolean active) {
    mBoundaryRecoveryActive = active;
    mRecoverySymbolCount = 0;
}

public boolean processWithSoftSyncDetect(float softSymbol, Dibit symbol) {
    // Track recovery window
    if(mBoundaryRecoveryActive) {
        mRecoverySymbolCount++;
        if(mRecoverySymbolCount >= RECOVERY_WINDOW_SYMBOLS) {
            mBoundaryRecoveryActive = false;
        }
    }

    boolean validNIDDetected = process(symbol);

    // Soft sync detection
    if(mSoftSyncDetector.process(softSymbol) > SYNC_DETECTION_THRESHOLD) {
        syncDetected();
    }
    // Hard sync during recovery (more tolerant of bit errors)
    else if(mBoundaryRecoveryActive && mHardSyncDetector.process(symbol)) {
        syncDetected();
        mRecoverySyncCount++;
    }

    return validNIDDetected;
}
```

```java
// P25P1DecoderLSMv2.java - in detectTransmissionBoundary()
if(mInSilence && mPeakEnergy > 0) {
    mDemodulator.coldStartReset();
    mMessageFramer.coldStartReset();
    mMessageFramer.setBoundaryRecoveryActive(true);  // NEW
    mBoundaryResetCount++;
    mInSilence = false;
}
```

**Acceptance Criteria**:
- Hard sync detection active only during recovery window
- Recovery window correctly times out after 100ms
- HDU detection rate improves by additional >=5%

---

## T4: Implement TDU Recovery (Strategy 3)

**Objective**: Improve TDU detection by predicting transmission end from energy fade.

**Subtasks**:
1. Add energy derivative tracking to `P25P1DecoderLSMv2`
2. Add energy fade detection (>50% drop in 50ms)
3. Notify framer of impending transmission end
4. Lower sync threshold during fade window
5. Add TDU-specific assembly recovery in `P25P1MessageAssembler`

**Code Changes**:

```java
// P25P1DecoderLSMv2.java
private float mPreviousEnergyAverage = 0f;
private int mFadeWindowSamples = 0;
private static final int FADE_DETECTION_WINDOW = 1250;  // 50ms at 25kHz
private static final float FADE_THRESHOLD = 0.5f;       // 50% drop

private void detectTransmissionBoundary(float[] i, float[] q) {
    for(int idx = 0; idx < i.length; idx++) {
        // ... existing energy tracking ...

        // Track energy fade for TDU recovery
        mFadeWindowSamples++;
        if(mFadeWindowSamples >= FADE_DETECTION_WINDOW) {
            if(mPreviousEnergyAverage > 0 &&
               mEnergyAverage < mPreviousEnergyAverage * FADE_THRESHOLD &&
               !mInSilence) {
                // Energy fading rapidly - possible transmission end
                mMessageFramer.setFadeRecoveryActive(true);
            }
            mPreviousEnergyAverage = mEnergyAverage;
            mFadeWindowSamples = 0;
        }
    }
}
```

```java
// P25P1MessageFramer.java
private static final float SYNC_FADE_THRESHOLD = 40;
private boolean mFadeRecoveryActive = false;
private int mFadeRecoverySyncCount = 0;

public void setFadeRecoveryActive(boolean active) {
    mFadeRecoveryActive = active;
}

// In processWithSoftSyncDetect - add fade recovery sync
else if(mFadeRecoveryActive && syncScore > SYNC_FADE_THRESHOLD) {
    syncDetected();
    mFadeRecoverySyncCount++;
    mFadeRecoveryActive = false;  // One-shot
}
```

**Acceptance Criteria**:
- Energy fade detected within 50ms window
- TDU sync threshold lowered only during fade
- TDU detection rate improves by >=10%

---

## T5: Run Full Regression Suite

**Objective**: Validate all optimizations on complete sample set.

**Subtasks**:
1. Run `TransmissionScoringTest` on all 8 samples
2. Compare: LSM vs v2 baseline vs v2 optimized
3. Verify no regression in LDU count
4. Document per-sample results
5. Calculate aggregate improvements

**Acceptance Criteria**:
- All 8 samples processed
- LDU count >= baseline for each sample
- HDU improvement >= 15% aggregate
- TDU improvement >= 10% aggregate
- Results documented with deltas

---

## T6: Generate Final Report

**Objective**: Create comprehensive optimization report with executive summary.

**Subtasks**:
1. Create `report.md` with structure:
   - Executive Summary
   - Methodology
   - Strategy 1 Results
   - Strategy 2 Results
   - Strategy 3 Results
   - Aggregate Results
   - Conclusions
2. Generate delta tables for all metrics
3. Document diagnostic counter results
4. Include per-sample breakdown

**Report Structure**:

```markdown
# P25 LSM v2 Optimization Report

## Executive Summary

| Metric | LSM | v2 Baseline | v2 Optimized | Delta vs LSM | Delta vs Baseline |
|--------|-----|-------------|--------------|--------------|-------------------|
| Total LDUs | X | Y | Z | +N% | +M% |
| HDU Detection | X% | Y% | Z% | +N% | +M% |
| TDU Detection | X% | Y% | Z% | +N% | +M% |

## Optimization Strategies

### Strategy 1: Adaptive Sync Threshold
[Description, parameters, results]

### Strategy 2: Boundary Recovery Window
[Description, parameters, results]

### Strategy 3: TDU Recovery
[Description, parameters, results]

## Per-Sample Results
[8 sample breakdown]

## Diagnostic Analysis
[Counter analysis, timing data]

## Conclusions
[Summary, recommendations]
```

**Acceptance Criteria**:
- Executive summary with clear delta metrics
- All 3 strategies documented with outcomes
- Per-sample breakdown included
- Report validates success criteria SC-001 through SC-008
