# Implementation Plan: Sync Failure Investigation & Decoder Improvements

## Overview

This investigation will analyze the 25 sync failure transmissions identified in 007-missed-transmission-analysis to understand why the decoder fails to acquire sync despite adequate signal energy. We will also investigate why the correlation between signal quality and decode success is weak (-0.075).

## Architecture

### Analysis Pipeline

```
1. SyncFailureInvestigator.java (NEW)
   ├── Extract 25 sync failure transmissions
   ├── Run detailed signal analysis on each
   ├── Calculate alternative signal metrics
   ├── Compare successful vs failed transmissions
   └── Generate investigation report

2. SignalProfile.java (NEW)
   ├── Energy envelope over time
   ├── SNR estimation
   ├── Timing stability metric
   ├── Energy variance (signal stability)
   └── Preamble energy profile
```

### Key Analysis Metrics

| Metric | Description | Purpose |
|--------|-------------|---------|
| Energy Envelope | Energy samples over transmission | Identify fading patterns |
| SNR Estimate | Ratio of signal to noise floor | Better quality indicator |
| Energy Variance | Standard deviation of energy | Signal stability measure |
| Preamble Energy | First 50ms of transmission | Sync acquisition window |
| Energy Slope | Rate of energy change at start | TX ramp-up detection |

## Investigation Approach

### Phase 1: Extract and Profile Sync Failures

For each of the 25 sync failure transmissions:
1. Extract baseband samples using TransmissionExtractor
2. Generate detailed SignalProfile with multiple metrics
3. Examine energy envelope at transmission start (first 100ms)
4. Look for patterns in signal characteristics

### Phase 2: Compare Successful vs Failed Transmissions

1. Select 25 successfully decoded transmissions with similar energy levels
2. Generate SignalProfiles for comparison
3. Calculate statistical differences between groups
4. Identify discriminating factors

### Phase 3: Signal Quality Metric Correlation

Test these alternative metrics for correlation with decode success:
1. **Energy Variance** - Does stable signal predict success?
2. **Preamble Energy** - Does strong start predict success?
3. **Energy Slope at Start** - Does TX ramp matter?
4. **Peak-to-Average Ratio** - Signal characteristics indicator

### Phase 4: Root Cause Categorization

Based on analysis, categorize sync failures:
- **WEAK_PREAMBLE**: Transmission starts weak, decoder misses initial sync
- **RAPID_FADE**: Signal drops before sync acquisition completes
- **LATE_START**: Energy detected but sync pattern delayed
- **NOISE_INTERFERENCE**: Adequate energy but high variance
- **TIMING_DRIFT**: Symbol timing issues visible in signal
- **INDETERMINATE**: No clear pattern identified

## Implementation Strategy

### Signal Analysis Approach

The current correlation (-0.075) uses simple average energy. The weak correlation suggests:

1. **Energy is not the limiting factor** - Sync failures occur across the energy range
2. **Something else matters** - Timing, preamble quality, or signal stability

Key hypothesis to test:
- Do sync failures have weaker **initial** energy (first 100ms)?
- Do sync failures have higher **energy variance** (unstable signal)?
- Do sync failures have different **energy slope** at transmission start?

### Code Structure

```java
// SyncFailureInvestigator.java - Main analysis tool
class SyncFailureInvestigator {
    void investigate(List<File> files);
    SignalProfile analyzeTransmission(File source, Transmission tx);
    void compareSuccessVsFailed(List<SignalProfile> success, List<SignalProfile> failed);
    void calculateCorrelations(List<TransmissionDecodeResult> results);
    void categorizeFailures(List<SyncFailureCase> failures);
    void printReport();
}

// SignalProfile.java - Detailed signal characterization
record SignalProfile(
    Transmission transmission,
    float[] energyEnvelope,      // Sampled energy over time
    float avgEnergy,             // Simple average
    float energyVariance,        // Stability measure
    float preambleEnergy,        // First 100ms average
    float energySlope,           // Rate of change at start
    float peakToAverage,         // Signal characteristic
    float snrEstimate            // Estimated SNR
) {}

// SyncFailureCase.java - Analysis result per failure
record SyncFailureCase(
    MissedTransmission missed,
    SignalProfile profile,
    SyncFailureCause cause,
    String evidence
) {}

// SyncFailureCause.java - Root cause categories
enum SyncFailureCause {
    WEAK_PREAMBLE,
    RAPID_FADE,
    LATE_START,
    NOISE_INTERFERENCE,
    TIMING_DRIFT,
    INDETERMINATE
}
```

## Decoder Improvement Targets

Based on typical sync failure patterns, potential improvements:

1. **Lower initial sync threshold** - If preamble is consistently weaker
2. **Extended acquisition window** - If sync pattern appears later
3. **Aggressive re-sync** - If signal recovers after initial fade
4. **Noise-tolerant sync** - If high variance correlates with failures

These improvements will be informed by the investigation results.

## Files to Create

| File | Purpose |
|------|---------|
| `SyncFailureInvestigator.java` | Main investigation tool |
| `SignalProfile.java` | Detailed signal characterization |
| `SyncFailureCase.java` | Analysis result per failure |
| `SyncFailureCause.java` | Root cause enum |

## Files to Modify

| File | Change |
|------|--------|
| `MissedTransmissionAnalyzer.java` | Export sync failures for detailed analysis |

## Verification

1. Run investigation on 8-file test suite
2. Verify all 25 sync failures have SignalProfiles
3. Verify statistical comparisons are generated
4. Verify at least 80% are categorized (not INDETERMINATE)
5. Verify correlation values for alternative metrics

## Expected Output

```
=== SYNC FAILURE INVESTIGATION ===

Files: 8
Sync Failures Analyzed: 25

=== SIGNAL METRIC CORRELATIONS ===
Metric                  Correlation  Significance
-----------------------------------------------
Average Energy          -0.075       Weak (baseline)
Energy Variance         +0.423       Moderate **
Preamble Energy         +0.312       Moderate **
Energy Slope            +0.189       Weak
Peak-to-Average Ratio   +0.067       Weak

=== SYNC FAILURE CAUSE DISTRIBUTION ===
WEAK_PREAMBLE:      12 (48.0%)
RAPID_FADE:          5 (20.0%)
LATE_START:          3 (12.0%)
NOISE_INTERFERENCE:  2 (8.0%)
INDETERMINATE:       3 (12.0%)

=== KEY FINDINGS ===
1. Energy variance shows moderate correlation with decode success
   - High variance signals fail more often regardless of average energy
   - Suggests signal stability matters more than strength

2. Preamble energy shows moderate correlation
   - Weak transmission starts correlate with sync failure
   - 48% of failures have weak preamble despite adequate average energy

=== RECOMMENDATIONS ===
1. PRIORITY HIGH: Address WEAK_PREAMBLE failures (48%)
   - Consider lowering initial sync threshold for first 100ms
   - Current SYNC_FALLBACK_THRESHOLD (52) may be too high

2. PRIORITY MEDIUM: Address RAPID_FADE failures (20%)
   - Improve recovery sync during energy transitions
   - Consider shorter fade detection window

3. Signal quality metrics should include energy variance and preamble energy
   - Current energy-only correlation is misleading
```
