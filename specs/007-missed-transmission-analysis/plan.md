# Implementation Plan: Missed Transmission Analysis

## Overview

Extend the existing transmission detection analysis infrastructure to provide deep analysis of missed transmissions and low decode rate transmissions. This feature categorizes failures, correlates signal quality with decode success, and identifies optimization opportunities.

## Architecture

### New Components

```
MissedTransmissionAnalyzer.java
├── FailureCategory enum (TOO_SHORT, WEAK_SIGNAL, SYNC_FAILURE, UNKNOWN)
├── MissedTransmission record (extends failure info)
├── SignalQualityBin class (aggregates by signal tier)
├── AnalysisReport class (structured output)
└── main() - CLI tool
```

### Integration Points

| Existing Component | How It's Used |
|---|---|
| TransmissionDetectionAnalyzer | Provides FileResult with TransmissionDecodeResult list |
| TransmissionDecodeResult | Source data for failure categorization |
| Transmission | Signal metrics (peakEnergy, avgEnergy, duration) |
| TransmissionExtractor | Extract missed transmissions for manual inspection |
| DetectionMetrics | Base aggregate statistics |

## Data Flow

```
1. TransmissionDetectionAnalyzer runs on files
   └── Returns List<TransmissionDecodeResult> per file

2. MissedTransmissionAnalyzer filters MISSED status
   └── Creates List<MissedTransmission> with failure categories

3. FailureCategorizer determines cause:
   ├── TOO_SHORT: duration < 180ms
   ├── WEAK_SIGNAL: avgEnergy < threshold (bottom 10%)
   ├── SYNC_FAILURE: adequate signal but no LDUs
   └── UNKNOWN: requires manual inspection

4. SignalQualityBinner groups all transmissions by energy:
   └── Creates 10 bins, calculates decode rate per bin

5. OptimizationAnalyzer identifies "sweet spot":
   └── Signal range with highest improvement potential

6. AnalysisReport generates actionable summary
```

## Key Entities

### FailureCategory (enum)
```java
TOO_SHORT,      // Duration < 180ms (one LDU)
WEAK_SIGNAL,    // Below energy threshold
SYNC_FAILURE,   // Adequate signal, no sync acquired
INCOMPLETE,     // Cut off at recording boundary
UNKNOWN         // Requires manual inspection
```

### MissedTransmission (record)
```java
record MissedTransmission(
    TransmissionDecodeResult result,
    FailureCategory category,
    String diagnosticInfo
)
```

### SignalQualityBin (class)
```java
class SignalQualityBin {
    float minEnergy, maxEnergy;
    int totalTransmissions;
    int lsmDecoded, v2Decoded, missed;
    // Calculate decode rates per tier
}
```

### LowRateTransmission (record)
```java
record LowRateTransmission(
    TransmissionDecodeResult result,
    double decodeRate,        // Best of LSM/v2
    String diagnosticInfo     // Timing gaps identified
)
```

## Implementation Phases

### Phase 1: Failure Categorization
- Add FailureCategory enum
- Implement categorization logic based on spec thresholds
- Create MissedTransmission record with diagnostic info

### Phase 2: Low Decode Rate Detection
- Filter transmissions below configurable threshold (default 20%)
- Identify characteristics (duration, signal strength, position)
- Compare with high-rate transmissions for contrast

### Phase 3: Signal Quality Correlation
- Create 10 signal quality bins based on observed energy range
- Aggregate decode statistics per bin
- Calculate correlation coefficient

### Phase 4: Optimization Opportunity Analysis
- Identify "optimization zone" (signal range with most failed decodes)
- Calculate potential improvement if failures were recovered
- Generate actionable recommendations

### Phase 5: Extraction Support
- Extend TransmissionExtractor for missed transmission extraction
- Add batch extraction for all missed transmissions
- Descriptive naming with failure category

### Phase 6: Report Generation
- Structured summary with all metrics
- Failure distribution breakdown
- Signal quality correlation table
- Actionable recommendations

## Thresholds (from spec)

| Threshold | Value | Source |
|---|---|---|
| TOO_SHORT | < 180ms | One LDU duration |
| WEAK_SIGNAL | Bottom 10% of energy | Configurable |
| LOW_DECODE_RATE | < 20% | Configurable default |
| LDU_DURATION_MS | 180 | P25 Phase 1 spec |

## Output Format

```
=== MISSED TRANSMISSION ANALYSIS ===

Failure Category Distribution:
  Too Short (<180ms):     8 (25.8%)
  Weak Signal:            5 (16.1%)
  Sync Failure:          12 (38.7%)
  Incomplete (boundary):  3 (9.7%)
  Unknown:                3 (9.7%)
  --------------------------------
  Total Missed:          31 (13.5% of expected)

Low Decode Rate Transmissions (<20%):
  Count: 15
  Characteristics:
    - Average duration: 890ms
    - Average signal: 0.023
    - Common pattern: First 1-2 LDUs only

Signal Quality Correlation:
  Bin     Energy Range    TX Count   Decode Rate   Missed
  ------- --------------- ---------- ------------- -------
  1       0.001-0.010          45        42.2%       12
  2       0.010-0.020          38        71.1%        8
  ...

Optimization Opportunity Zone:
  Signal range: 0.010 - 0.030
  Transmissions: 72 (31% of total)
  Current decode rate: 58.3%
  Potential gain: +30 transmissions if optimized

Recommendations:
  1. Focus sync acquisition on signal range 0.010-0.030
  2. Investigate 12 sync failure cases with adequate signal
  3. Consider shorter holdover for fade recovery
```

## Files to Create/Modify

| File | Action | Description |
|---|---|---|
| MissedTransmissionAnalyzer.java | CREATE | Main analyzer with CLI |
| FailureCategory.java | CREATE | Enum for failure types |
| MissedTransmission.java | CREATE | Record with failure info |
| SignalQualityBin.java | CREATE | Signal tier aggregation |
| LowRateTransmission.java | CREATE | Low decode rate analysis |
| TransmissionExtractor.java | MODIFY | Add extractMissedTransmissions() |

## Verification

1. Run on 8-file test suite
2. Verify all 31 missed transmissions are categorized
3. Verify signal correlation report generates
4. Verify extraction creates valid WAV files
5. Total analysis completes in < 10 minutes
