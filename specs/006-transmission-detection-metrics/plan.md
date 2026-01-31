# Implementation Plan: Transmission Detection Metrics

**Feature Branch**: `006-transmission-detection-metrics`
**Created**: 2026-01-31
**Spec**: [spec.md](./spec.md)

## Overview

This plan extends the existing transmission scoring infrastructure to provide detailed metrics comparing expected transmissions (from signal detection) against actual decode performance for both LSM and LSM v2 decoders.

## Architecture Decision

**Approach**: Extend existing test infrastructure rather than create new standalone tools.

**Rationale**:
- `TransmissionMapper` already provides signal-based transmission detection
- `TransmissionScoringTest` already runs both decoders and scores transmissions
- `Transmission` record already has `expectedLDUs()` method
- Adding metrics reporting to existing infrastructure minimizes code duplication

## Component Design

### 1. DetectionMetrics Class (New)

Aggregate statistics container for transmission detection vs decode performance.

```
DetectionMetrics
├── totalExpectedTransmissions: int
├── totalExpectedLDUs: int
├── lsmDecodedTransmissions: int
├── lsmDecodedLDUs: int
├── v2DecodedTransmissions: int
├── v2DecodedLDUs: int
├── missedTransmissions: int (neither decoded)
├── lsmOnlyTransmissions: int
├── v2OnlyTransmissions: int
└── methods: decodeRates(), summaryString(), etc.
```

### 2. TransmissionDecodeResult Class (New)

Per-transmission decode result combining expected with actual.

```
TransmissionDecodeResult
├── transmission: Transmission
├── lsmLduCount: int
├── v2LduCount: int
├── lsmDecodeRate: double (actual/expected)
├── v2DecodeRate: double
├── status: enum (BOTH, LSM_ONLY, V2_ONLY, MISSED)
└── signalQuality: float (avgEnergy normalized)
```

### 3. Enhanced TransmissionScoringTest

Add new report sections:
- Expected vs Decoded summary table
- Per-transmission decode rates
- Missed transmission list
- Signal quality correlation analysis

### 4. Multi-File Analysis Runner (New)

New `TransmissionDetectionAnalyzer` class that processes multiple files and aggregates results.

```
TransmissionDetectionAnalyzer
├── analyzeFile(File): FileAnalysisResult
├── analyzeFiles(List<File>): AggregateResult
└── printReport(AggregateResult)
```

## Data Flow

```
Baseband WAV File
       │
       ▼
┌──────────────────┐
│TransmissionMapper│ → List<Transmission> (expected)
└──────────────────┘
       │
       ├──────────────────────────┐
       ▼                          ▼
┌──────────────┐          ┌──────────────┐
│  LSM Decoder │          │ LSM v2 Decoder│
└──────────────┘          └──────────────┘
       │                          │
       ▼                          ▼
  DecoderStats                DecoderStats
       │                          │
       └──────────┬───────────────┘
                  ▼
         ┌────────────────┐
         │TransmissionScorer│ → List<TransmissionDecodeResult>
         └────────────────┘
                  │
                  ▼
         ┌────────────────┐
         │DetectionMetrics│ → Aggregate statistics
         └────────────────┘
                  │
                  ▼
            Report Output
```

## Report Format

### Per-File Summary
```
=== TRANSMISSION DETECTION METRICS ===
File: sample.wav

Expected Transmissions: 13
Expected LDUs:          1968

                    LSM        v2      Delta
Decoded TX:          11        13        +2
Decoded LDUs:       407       446       +39
Decode Rate:      20.7%     22.7%     +2.0%

Transmission Status:
  Both decoded:       10 (76.9%)
  v2 only:             3 (23.1%)
  LSM only:            0 (0.0%)
  Neither (missed):    0 (0.0%)
```

### Aggregate Summary (Multi-File)
```
=== AGGREGATE DETECTION METRICS (8 files) ===

Total Expected TX:     142
Total Expected LDUs:  15,234

                    LSM        v2      Delta
Total Decoded TX:    128       139       +11
Total Decoded LDUs: 5,842     6,926   +1,084
Overall Decode Rate: 38.3%    45.5%    +7.2%

v2 Improvement:
  Additional TX decoded:    +11 (+8.6%)
  Additional LDUs decoded: +1,084 (+18.5%)
```

## File Changes

| File | Change Type | Description |
|------|-------------|-------------|
| `DetectionMetrics.java` | New | Aggregate metrics container |
| `TransmissionDecodeResult.java` | New | Per-transmission result record |
| `TransmissionScorer.java` | Modify | Return TransmissionDecodeResult |
| `TransmissionScoringTest.java` | Modify | Add detection metrics report |
| `TransmissionDetectionAnalyzer.java` | New | Multi-file analysis runner |

## Implementation Order

1. Create `TransmissionDecodeResult` record (foundation)
2. Create `DetectionMetrics` class (aggregation)
3. Update `TransmissionScorer` to produce `TransmissionDecodeResult`
4. Update `TransmissionScoringTest` with new report sections
5. Create `TransmissionDetectionAnalyzer` for multi-file analysis
6. Test on sample files and verify metrics

## Success Validation

Run analyzer on 8 sample files and verify:
- [ ] All expected transmissions detected (compare with existing output)
- [ ] Decode rates match LDU counts / expected LDUs
- [ ] v2 shows expected ~18% LDU improvement
- [ ] Missed transmissions correctly identified
- [ ] Report format is clear and actionable
