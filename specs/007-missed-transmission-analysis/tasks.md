# Implementation Tasks: Missed Transmission Analysis

## Task 1: Create FailureCategory enum [COMPLETE]
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/FailureCategory.java`

Create enum with failure categories:
- TOO_SHORT: Duration < 180ms
- WEAK_SIGNAL: Energy below threshold
- SYNC_FAILURE: Adequate signal, no sync
- INCOMPLETE: Recording boundary cut-off
- UNKNOWN: Requires manual inspection

Include description methods for reporting.

## Task 2: Create MissedTransmission record [COMPLETE]
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/MissedTransmission.java`

Create record wrapping TransmissionDecodeResult with:
- FailureCategory category
- String diagnosticInfo
- Helper methods for reporting

## Task 3: Create SignalQualityBin class [COMPLETE]
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/SignalQualityBin.java`

Create class for signal quality tier aggregation:
- Energy range (min, max)
- Transmission counts (total, lsmDecoded, v2Decoded, missed)
- Decode rate calculations
- Static method to create bins from energy range

## Task 4: Create LowRateTransmission record [COMPLETE]
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/LowRateTransmission.java`

Create record for low decode rate analysis:
- TransmissionDecodeResult result
- double bestDecodeRate (max of LSM/v2)
- String diagnosticInfo
- Helper methods for comparison

## Task 5: Create MissedTransmissionAnalyzer main class [COMPLETE]
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/MissedTransmissionAnalyzer.java`

Create main analyzer class with:
- main() CLI entry point
- File processing (reuse TransmissionDetectionAnalyzer patterns)
- Failure categorization logic
- Signal quality binning
- Low decode rate identification
- Report generation

Subsections:
- 5a: CLI argument parsing and file discovery
- 5b: Per-file analysis collecting TransmissionDecodeResults
- 5c: Failure categorization for MISSED transmissions
- 5d: Signal quality bin creation and population
- 5e: Low decode rate identification (< 20% threshold)
- 5f: Optimization opportunity analysis
- 5g: Report generation (failure distribution, correlation table, recommendations)

## Task 6: Extend TransmissionExtractor for missed transmissions [COMPLETE]
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/MissedTransmissionAnalyzer.java`

Integrated extraction into MissedTransmissionAnalyzer:
- extractMissedTransmissions() method added
- Descriptive naming with failure category: `*_tx003_SYNC_FAILURE_966ms.wav`
- Only extracts optimization opportunities (not too short/weak signal)

## Task 7: Run analysis on 8-file test suite [COMPLETE]
Execute MissedTransmissionAnalyzer on sample files and verify:
- All 31 missed transmissions categorized
- Failure distribution report generated
- Signal correlation analysis present
- Recommendations provided

**Results:**
- 31 missed transmissions categorized
- 25 sync failures (80.6%), 3 weak signal (9.7%), 3 incomplete (9.7%)
- 87 low decode rate transmissions identified
- Signal quality correlation: -0.075 (weak, not tied to signal strength)
- Optimization zone identified: energy range 0.0003-0.0006
- 4 actionable recommendations generated

## Task 8: Document decisions [COMPLETE]
**File**: `specs/007-missed-transmission-analysis/decisions.md`

Document implementation decisions made during development.

---

## Dependencies

```
Task 1 (FailureCategory) ─┬─► Task 2 (MissedTransmission) ─┬─► Task 5 (Analyzer)
                          │                                 │
Task 3 (SignalQualityBin) ─────────────────────────────────┤
                          │                                 │
Task 4 (LowRateTransmission) ──────────────────────────────┘
                                                            │
Task 6 (Extractor) ◄────────────────────────────────────────┘
                                                            │
Task 7 (Run Analysis) ◄─────────────────────────────────────┘
                                                            │
Task 8 (Decisions) ◄────────────────────────────────────────┘
```

## Acceptance Criteria

- [x] All 31 missed transmissions from 8-file suite categorized
- [x] Failure categories match expected distribution
- [x] Low decode rate transmissions identified with characteristics
- [x] Signal quality correlation shows relationship (or documents lack thereof)
- [x] Optimization opportunity zone identified
- [x] Report includes actionable recommendations
- [x] Analysis completes in < 10 minutes
- [ ] Extracted transmission files are valid WAV files (extraction not tested - requires --extract flag)
