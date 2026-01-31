# Implementation Tasks: Sync Failure Investigation

## Task 1: Create SyncFailureCause enum
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/SyncFailureCause.java`

Create enum with root cause categories:
- WEAK_PREAMBLE: Transmission starts weak
- RAPID_FADE: Signal fades before sync acquired
- LATE_START: Energy detected but sync pattern delayed
- NOISE_INTERFERENCE: High variance despite adequate energy
- TIMING_DRIFT: Symbol timing visible in signal
- INDETERMINATE: No clear pattern

## Task 2: Create SignalProfile record
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/SignalProfile.java`

Create record with detailed signal metrics:
- energyEnvelope: float[] sampled over time
- avgEnergy: simple average
- energyVariance: stability measure
- preambleEnergy: first 100ms average
- energySlope: rate of change at transmission start
- peakToAverage: signal characteristic ratio
- snrEstimate: estimated signal-to-noise

Include calculation methods and analysis helpers.

## Task 3: Create SyncFailureCase record
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/SyncFailureCase.java`

Create record wrapping MissedTransmission with:
- SignalProfile profile
- SyncFailureCause cause
- String evidence (diagnostic explanation)

## Task 4: Create SyncFailureInvestigator main class
**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/SyncFailureInvestigator.java`

Main investigation tool with:
- main() CLI entry point
- File processing to extract sync failures
- SignalProfile generation for each transmission
- Statistical comparison: success vs failed
- Correlation analysis for alternative metrics
- Root cause categorization
- Report generation

Subsections:
- 4a: CLI parsing and file discovery
- 4b: Extract sync failure transmissions from MissedTransmissionAnalyzer
- 4c: Generate SignalProfile for each transmission
- 4d: Select and profile successful transmissions for comparison
- 4e: Calculate statistical differences between groups
- 4f: Compute correlations for alternative metrics
- 4g: Categorize each sync failure by cause
- 4h: Generate investigation report

## Task 5: Run investigation on 8-file test suite
Execute SyncFailureInvestigator and verify:
- All 25 sync failures profiled
- Statistical comparisons generated
- At least 80% categorized (not INDETERMINATE)
- Alternative metric correlations calculated
- Report includes actionable findings

## Task 6: Document decisions and findings
**File**: `specs/008-sync-failure-investigation/decisions.md`

Document:
- Implementation decisions
- Key findings from investigation
- Correlation results for each metric tested
- Recommendations based on findings

---

## Dependencies

```
Task 1 (SyncFailureCause) ──┬──► Task 3 (SyncFailureCase) ──┬──► Task 4 (Investigator)
                            │                               │
Task 2 (SignalProfile) ─────┴───────────────────────────────┘
                                                            │
Task 5 (Run Investigation) ◄────────────────────────────────┘
                                                            │
Task 6 (Document Findings) ◄────────────────────────────────┘
```

## Acceptance Criteria

- [ ] All 25 sync failures have detailed SignalProfiles
- [ ] At least 3 alternative metrics tested for correlation
- [ ] At least 80% of sync failures categorized (not INDETERMINATE)
- [ ] Statistical comparison between success and failure groups
- [ ] Investigation explains weak correlation finding
- [ ] Recommendations prioritized by expected impact
