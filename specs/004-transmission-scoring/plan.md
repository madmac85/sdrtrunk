# Implementation Plan: Transmission Detection and Decode Scoring System

**Feature Branch**: `004-transmission-scoring`
**Created**: 2026-01-28
**Spec**: [spec.md](./spec.md)

## Architecture Overview

### Existing Infrastructure to Leverage

1. **MissedTransmissionAnalyzer** - Already has signal period detection with energy thresholds
2. **LSMv2ComparisonTest** - Framework for running both decoders and collecting statistics
3. **P25P1DecoderLSMv2** - Energy detection constants and transmission boundary logic
4. **ComplexWaveSource** - WAV file reading for baseband samples

### New Components

```
TransmissionMapper                 TransmissionScorer
      │                                  │
      ▼                                  ▼
┌─────────────────┐              ┌─────────────────┐
│ Transmission    │              │ TransmissionScore│
│ - startMs       │◄─────────────│ - transmission   │
│ - endMs         │              │ - lduCount       │
│ - duration      │              │ - expectedLDUs   │
│ - peakEnergy    │              │ - hasHDU         │
│ - isComplete    │              │ - hasTDU         │
└─────────────────┘              │ - score          │
                                 └─────────────────┘
                                         │
                                         ▼
                                 ┌─────────────────┐
                                 │ TransmissionReport│
                                 │ - allScores      │
                                 │ - avgScore       │
                                 │ - worstList      │
                                 │ - regressions    │
                                 └─────────────────┘
```

## Implementation Components

### Component 1: Transmission Record

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/Transmission.java`

Simple record to represent a detected transmission:

```java
public record Transmission(
    int index,           // Transmission number in file
    long startMs,        // Start timestamp
    long endMs,          // End timestamp
    float peakEnergy,    // Peak signal energy
    float avgEnergy,     // Average signal energy
    boolean isComplete   // False if cut off at recording boundary
) {
    public long durationMs() { return endMs - startMs; }
    public int expectedLDUs() { return Math.max(1, (int)(durationMs() / 180)); }
}
```

### Component 2: Transmission Mapper

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionMapper.java`

Extends MissedTransmissionAnalyzer's energy detection to produce a complete transmission map:

```java
public class TransmissionMapper {
    // Configuration
    private static final long MIN_TRANSMISSION_MS = 180;  // Minimum 1 LDU
    private static final long MIN_GAP_MS = 500;           // Gap to split transmissions
    private static final long BUFFER_MS = 100;            // Buffer for slice extraction

    // Reuse energy detection from MissedTransmissionAnalyzer

    public List<Transmission> mapTransmissions(EnergyProfile profile);
    public void extractSlice(Path inputWav, Transmission tx, Path outputWav);
}
```

### Component 3: Transmission Score

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScore.java`

Metrics for a single transmission's decode quality:

```java
public record TransmissionScore(
    Transmission transmission,
    int lsmLduCount,
    int v2LduCount,
    boolean lsmHasHDU,
    boolean v2HasHDU,
    boolean lsmHasTDU,
    boolean v2HasTDU,
    List<Long> lsmLduTimestamps,
    List<Long> v2LduTimestamps
) {
    public double lsmScore() {
        return transmission.expectedLDUs() > 0 ?
            (double)lsmLduCount / transmission.expectedLDUs() * 100.0 : 0;
    }
    public double v2Score() { ... }
    public boolean isV2Regression() { return v2LduCount < lsmLduCount; }
}
```

### Component 4: Transmission Scorer

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScorer.java`

Correlates decoded messages with transmission boundaries:

```java
public class TransmissionScorer {
    public TransmissionScore score(
        Transmission tx,
        DecoderStats lsmStats,
        DecoderStats v2Stats
    );

    // Find LDUs that fall within transmission boundaries
    private int countLDUsInTransmission(Transmission tx, List<Long> lduTimestamps);

    // Check for HDU near transmission start
    private boolean hasHDU(Transmission tx, List<Long> hduTimestamps);

    // Check for TDU/TDULC near transmission end
    private boolean hasTDU(Transmission tx, List<Long> tduTimestamps);
}
```

### Component 5: Enhanced Decoder Stats

**File**: Modify `LSMv2ComparisonTest.DecoderStats`

Add tracking for HDU and TDU timestamps:

```java
private static class DecoderStats {
    // Existing fields...
    List<Long> hduTimestamps = new ArrayList<>();  // NEW
    List<Long> tduTimestamps = new ArrayList<>();  // NEW

    // In message listener, track HDU/TDU:
    if(duid == P25P1DataUnitID.HEADER_DATA_UNIT)
        hduTimestamps.add(timestamp);
    if(duid == P25P1DataUnitID.TERMINATOR_DATA_UNIT ||
       duid == P25P1DataUnitID.TERMINATOR_DATA_UNIT_LINK_CONTROL)
        tduTimestamps.add(timestamp);
}
```

### Component 6: Transmission Scoring Test

**File**: `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScoringTest.java`

Main test class that integrates all components:

```java
public class TransmissionScoringTest {
    public static void main(String[] args) {
        // 1. Load baseband WAV
        // 2. Map transmissions (energy-based detection)
        // 3. Run LSM and LSM v2 decoders
        // 4. Score each transmission
        // 5. Generate report
        // 6. Optionally extract problematic slices
    }
}
```

### Component 7: Gradle Task

**File**: `build.gradle`

```groovy
tasks.register('runTransmissionScoring', JavaExec) {
    dependsOn testClasses
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'io.github.dsheirer.module.decode.p25.phase1.TransmissionScoringTest'
    // Args: --basebandFile, --nac, --outputDir, --extractSlices
}
```

## Design Decisions

### Decision 1: Energy Detection Parameters

**Chosen**: Reuse parameters from P25P1DecoderLSMv2 and MissedTransmissionAnalyzer

**Rationale**:
- Already tuned for P25 signal detection
- Ensures consistency between decoder and mapper
- Parameters: ENERGY_EMA_FACTOR=0.002, SILENCE_RATIO=0.15, SILENCE_DURATION=500ms

### Decision 2: Transmission Boundary Buffer

**Chosen**: 100ms before and after

**Rationale**:
- Spec specifies configurable default of 100ms
- Allows capturing HDU that appears just before audio starts
- Allows capturing TDU that appears after audio ends
- Not so long that it captures adjacent transmissions

### Decision 3: Expected LDU Calculation

**Chosen**: duration_ms / 180, minimum 1

**Rationale**:
- LDU frame is exactly 180ms per P25 specification
- Minimum of 1 prevents division issues for very short transmissions
- Simple and deterministic

### Decision 4: Score Calculation

**Chosen**: (actual LDUs / expected LDUs) * 100, capped at 100%

**Rationale**:
- Percentage is intuitive and comparable
- Cap at 100% handles cases where duration estimate is slightly off
- Score can exceed 100% if transmission boundaries are inexact (acceptable)

## File Changes Summary

| File | Change Type | Description |
|------|-------------|-------------|
| `Transmission.java` | NEW | Record for transmission data |
| `TransmissionScore.java` | NEW | Record for transmission score |
| `TransmissionMapper.java` | NEW | Energy-based transmission detection |
| `TransmissionScorer.java` | NEW | Message-to-transmission correlation |
| `TransmissionScoringTest.java` | NEW | Main test runner |
| `LSMv2ComparisonTest.java` | MODIFY | Add HDU/TDU tracking |
| `build.gradle` | MODIFY | Add Gradle task |

## Output Format

```
=== Transmission Scoring Report ===
File: example.wav | NAC: 117 | Transmissions: 23

ID    Start     End    Duration  Expected  LSM LDU  v2 LDU  LSM %   v2 %  HDU  TDU  Flags
--------------------------------------------------------------------------------------------
1     1234     5678      4444       25        22       24   88.0%  96.0%   Y    Y
2     6789    12345      5556       31        28       30   90.3%  96.8%   Y    Y
3    13456    15678      2222       12         0        8    0.0%  66.7%   N    Y   ***
...

=== Summary ===
Total Transmissions: 23
Complete (HDU+TDU):  19 (82.6%)
Avg LSM Score:       78.4%
Avg v2 Score:        91.2%
v2 Improvement:      +12.8%

=== Regressions (v2 < LSM) ===
ID 7: v2=75.0% vs LSM=80.0% (1 LDU worse)
ID 15: v2=50.0% vs LSM=66.7% (2 LDUs worse)

=== Worst Transmissions (v2) ===
1. ID 3: 66.7% (8/12 LDUs) - No HDU
2. ID 15: 50.0% (3/6 LDUs)
...
```

## Execution Order

```
1. TransmissionMapper           (independent)
2. DecoderStats enhancement     (independent)
3. Transmission record          (parallel with above)
4. TransmissionScore record     (parallel with above)
5. TransmissionScorer           (depends on 1-4)
6. TransmissionScoringTest      (depends on 5)
7. Gradle task                  (depends on 6)
8. Test run and validation      (depends on 7)
```

## Testing Strategy

1. **Unit Test**: TransmissionMapper on sample file, verify transmission count matches manual inspection
2. **Integration Test**: Full scoring pipeline on known recording
3. **Regression Test**: Compare scoring output on same files used for LSMv2ComparisonTest
4. **Validation**: Manual verification of top 3 worst-scoring transmissions
