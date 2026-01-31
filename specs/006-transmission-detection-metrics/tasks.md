# Implementation Tasks: Transmission Detection Metrics

**Feature Branch**: `006-transmission-detection-metrics`
**Created**: 2026-01-31
**Plan**: [plan.md](./plan.md)

## Task List

### Task 1: Create TransmissionDecodeResult Record
**Status**: Complete
**Files**: `src/test/java/.../p25/phase1/TransmissionDecodeResult.java`

Create a record that combines expected transmission with actual decode results:
- transmission: Transmission (the expected transmission)
- lsmLduCount: int
- v2LduCount: int
- lsmBitErrors: int
- v2BitErrors: int
- Add calculated methods: lsmDecodeRate(), v2DecodeRate(), status()
- Status enum: BOTH_DECODED, LSM_ONLY, V2_ONLY, MISSED

---

### Task 2: Create DetectionMetrics Class
**Status**: Complete
**Files**: `src/test/java/.../p25/phase1/DetectionMetrics.java`

Create aggregate statistics class:
- Accumulate counts from List<TransmissionDecodeResult>
- Track: totalExpectedTx, totalExpectedLDUs, lsmDecodedTx, v2DecodedTx, etc.
- Calculate: overall decode rates, improvement percentages
- Provide summaryString() for report output

---

### Task 3: Update TransmissionScorer to Return TransmissionDecodeResult
**Status**: Complete
**Files**: `src/test/java/.../p25/phase1/TransmissionScorer.java`

Modify score() method to return TransmissionDecodeResult instead of TransmissionScore:
- Include all existing score data
- Add status classification (BOTH, LSM_ONLY, V2_ONLY, MISSED)
- Rename/refactor TransmissionScore → TransmissionDecodeResult or create adapter

---

### Task 4: Add Detection Metrics Report to TransmissionScoringTest
**Status**: Complete
**Files**: `src/test/java/.../p25/phase1/TransmissionScoringTest.java`

Add new printDetectionMetrics() method:
- Show expected vs decoded summary table
- Show transmission status breakdown
- Show v2 improvement metrics
- Integrate into runAnalysis() output

---

### Task 5: Create TransmissionDetectionAnalyzer for Multi-File Analysis
**Status**: Complete
**Files**: `src/test/java/.../p25/phase1/TransmissionDetectionAnalyzer.java`

Create standalone analyzer:
- main() accepts directory or list of WAV files
- Process each file with signal detection + both decoders
- Aggregate DetectionMetrics across all files
- Print per-file and aggregate reports

---

### Task 6: Run Analysis and Validate Results
**Status**: Complete

Execute analyzer on 8 sample files:
- Verify expected transmission counts match existing output
- Verify decode rates are calculated correctly
- Confirm v2 shows ~18% LDU improvement
- Ensure report format is clear

---

## Dependencies

```
Task 1 (TransmissionDecodeResult)
    │
    ├──► Task 2 (DetectionMetrics) ──┐
    │                                 │
    └──► Task 3 (TransmissionScorer) ─┼──► Task 4 (Report)
                                      │
                                      └──► Task 5 (Analyzer) ──► Task 6 (Validate)
```

## Completion Criteria

- [x] All 6 tasks completed
- [x] Code compiles without errors
- [x] Analysis runs successfully on sample files
- [x] Report shows expected vs decoded metrics
- [x] v2 improvement metrics are visible
