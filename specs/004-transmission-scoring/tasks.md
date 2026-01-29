# Implementation Tasks: Transmission Detection and Decode Scoring System

**Feature Branch**: `004-transmission-scoring`
**Created**: 2026-01-28
**Plan**: [plan.md](./plan.md)

## Task Overview

| ID | Task | Priority | Depends On | Status |
|----|------|----------|------------|--------|
| T1 | Create Transmission record | P1 | - | Done |
| T2 | Create TransmissionScore record | P1 | - | Done |
| T3 | Implement TransmissionMapper | P1 | T1 | Done |
| T4 | Enhance DecoderStats with HDU/TDU tracking | P1 | - | Done |
| T5 | Implement TransmissionScorer | P1 | T1, T2, T4 | Done |
| T6 | Create TransmissionScoringTest | P1 | T3, T5 | Done |
| T7 | Add Gradle task for scoring | P2 | T6 | Done |
| T8 | Add slice extraction capability | P2 | T3 | Done |
| T9 | Run validation tests | P1 | T6 | Done |

---

## Task Details

### T1: Create Transmission Record

**Priority**: P1
**Depends On**: None
**Status**: Done

**Description**:
Create a record class to represent a detected transmission from energy analysis.

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/Transmission.java`

**Implementation**:
```java
public record Transmission(
    int index,
    long startMs,
    long endMs,
    float peakEnergy,
    float avgEnergy,
    boolean isComplete
) {
    public long durationMs() { return endMs - startMs; }
    public int expectedLDUs() { return Math.max(1, (int)(durationMs() / 180)); }
}
```

**Acceptance Criteria**:
- [x] Record compiles and has all specified fields
- [x] durationMs() returns correct calculation
- [x] expectedLDUs() returns duration/180, minimum 1

---

### T2: Create TransmissionScore Record

**Priority**: P1
**Depends On**: None
**Status**: Done

**Description**:
Create a record class to hold scoring metrics for a transmission.

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScore.java`

**Implementation**:
```java
public record TransmissionScore(
    Transmission transmission,
    int lsmLduCount,
    int v2LduCount,
    boolean lsmHasHDU,
    boolean v2HasHDU,
    boolean lsmHasTDU,
    boolean v2HasTDU
) {
    public double lsmScore() { ... }
    public double v2Score() { ... }
    public boolean isV2Regression() { ... }
    public int delta() { return v2LduCount - lsmLduCount; }
}
```

**Acceptance Criteria**:
- [x] Record compiles with all fields
- [x] Score methods return percentage (0-100+)
- [x] isV2Regression correctly identifies when v2 < LSM

---

### T3: Implement TransmissionMapper

**Priority**: P1
**Depends On**: T1
**Status**: Done

**Description**:
Create mapper that detects transmission boundaries from energy profile.

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionMapper.java`

**Implementation**:
1. Reuse EnergyProfile from MissedTransmissionAnalyzer
2. Process I/Q samples to build energy profile
3. Find signal periods using threshold detection
4. Convert signal periods to Transmission records
5. Add method to extract slice to WAV file

**Key Methods**:
- `public List<Transmission> mapTransmissions(Path basebandWav)`
- `public EnergyProfile buildEnergyProfile(Path basebandWav)`

**Acceptance Criteria**:
- [x] Correctly identifies transmission boundaries from energy
- [x] Handles recording start/end edge cases
- [x] Marks incomplete transmissions appropriately
- [x] Gap threshold of 500ms splits transmissions correctly

---

### T4: Enhance DecoderStats with HDU/TDU Tracking

**Priority**: P1
**Depends On**: None
**Status**: Done

**Description**:
Modify DecoderStats in LSMv2ComparisonTest to track HDU and TDU timestamps.

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/LSMv2ComparisonTest.java`

**Changes**:
1. Add `List<Long> hduTimestamps = new ArrayList<>();`
2. Add `List<Long> tduTimestamps = new ArrayList<>();`
3. In message listener, track HEADER_DATA_UNIT timestamps
4. Track TERMINATOR_DATA_UNIT and TERMINATOR_DATA_UNIT_LINK_CONTROL timestamps

**Acceptance Criteria**:
- [x] HDU timestamps are captured
- [x] TDU/TDULC timestamps are captured
- [x] Existing test functionality unchanged

---

### T5: Implement TransmissionScorer

**Priority**: P1
**Depends On**: T1, T2, T4
**Status**: Done

**Description**:
Create scorer that correlates decoded messages with transmissions.

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScorer.java`

**Implementation**:
```java
public class TransmissionScorer {
    private static final long TOLERANCE_MS = 100;

    public TransmissionScore score(
        Transmission tx,
        DecoderStats lsmStats,
        DecoderStats v2Stats,
        long baseTimestamp
    );

    private int countLDUsInRange(long startMs, long endMs, List<Long> timestamps, long base);
    private boolean hasMessageInRange(long targetMs, long tolerance, List<Long> timestamps, long base);
}
```

**Acceptance Criteria**:
- [x] Correctly counts LDUs within transmission boundaries
- [x] Correctly detects HDU near transmission start
- [x] Correctly detects TDU near transmission end
- [x] Handles timestamp normalization

---

### T6: Create TransmissionScoringTest

**Priority**: P1
**Depends On**: T3, T5
**Status**: Done

**Description**:
Create main test class that integrates all components.

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScoringTest.java`

**Implementation**:
1. Parse command line args (baseband, NAC, output options)
2. Map transmissions using TransmissionMapper
3. Run both decoders (reuse runDecoder from LSMv2ComparisonTest)
4. Score each transmission
5. Generate formatted report
6. Rank by worst score
7. Flag regressions

**Output Format**:
- Table with per-transmission scores
- Summary statistics
- Regression list
- Worst transmissions list

**Acceptance Criteria**:
- [x] Runs end-to-end on sample file
- [x] Produces readable report
- [x] Correctly identifies regressions
- [x] Ranks transmissions by decode quality

---

### T7: Add Gradle Task for Scoring

**Priority**: P2
**Depends On**: T6
**Status**: Done

**Description**:
Add Gradle task to run the transmission scoring test.

**File**: `build.gradle`

**Implementation**:
```groovy
tasks.register('runTransmissionScoring', JavaExec) {
    dependsOn testClasses
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'io.github.dsheirer.module.decode.p25.phase1.TransmissionScoringTest'
}
```

**Acceptance Criteria**:
- [x] Task is visible in `./gradlew tasks`
- [x] Task runs the test correctly
- [x] Accepts command line parameters via -P flags

---

### T8: Add Slice Extraction Capability

**Priority**: P2
**Depends On**: T3
**Status**: Done

**Description**:
Add ability to extract individual transmissions to separate WAV files.

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionExtractor.java`

**Implementation**:
1. Calculate sample offset from timestamp: `offset = (timestamp_ms * sampleRate) / 1000`
2. Read source WAV file
3. Extract samples for transmission + buffer
4. Write to new WAV file with descriptive name

**Acceptance Criteria**:
- [x] Extracts correct portion of file
- [x] Adds configurable buffer before/after (200ms default)
- [x] Names output files descriptively (e.g., `basename_tx003_12345-15678.wav`)
- [x] Extracted slice decodes correctly (verified with runComparison)

---

### T9: Run Validation Tests

**Priority**: P1
**Depends On**: T6
**Status**: Done

**Description**:
Run the scoring test on sample files and validate results.

**Tasks**:
1. Run on all 8 sample files in _SAMPLES directory
2. Verify transmission count is reasonable (compare to LDU-based estimates)
3. Manually verify top 3 worst-scoring transmissions per file
4. Confirm no regressions in total LDU count

**Acceptance Criteria**:
- [x] All sample files process successfully
- [x] Transmission detection accuracy appears >95%
- [x] Report is clear and actionable
- [x] Regressions are correctly flagged

---

## Execution Order

```
T1 (Transmission) ─┐
                   ├─→ T3 (Mapper) ─┐
T2 (Score) ────────┤                ├─→ T6 (ScoringTest) ─→ T7 (Gradle) ─→ T9 (Validation)
                   │                │
T4 (DecoderStats) ─┴─→ T5 (Scorer) ─┘
                                   └─→ T8 (Extraction)
```

## Notes

- T1, T2, T4 can be done in parallel (no dependencies)
- T3 depends on T1 for Transmission record
- T5 depends on T1, T2, T4 for all data types
- T6 is the integration point requiring T3 and T5
- T7 and T8 are independent enhancements
- T9 validates the entire implementation

## Additional Files Created

During implementation, an additional file was created:

- `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TestComplexWaveSource.java` - Modified ComplexWaveSource that provides file-position-based timestamps instead of wall-clock time, enabling accurate correlation between decoded message timestamps and transmission boundaries.
