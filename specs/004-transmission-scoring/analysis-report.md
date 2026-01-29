# Transmission Scoring Analysis Report

**Date**: 2026-01-29
**Branch**: `004-transmission-scoring`

## Executive Summary

Analysis of 8 sample recordings reveals:
- **Total transmissions analyzed**: 230
- **v2 consistently outperforms LSM**: +1% to +7.5% improvement per file
- **Average decode rate is low**: 10-36% of expected LDUs captured
- **11 regressions identified** where v2 decoded fewer LDUs than LSM

## Per-File Results

| File | TX Count | Expected LDUs | LSM LDUs | v2 LDUs | LSM Avg | v2 Avg | Δ |
|------|----------|---------------|----------|---------|---------|--------|---|
| 155626_PD-W | 13 | 1,968 | 407 | 444 | 30.4% | 36.2% | +5.9% |
| 155903_PD-E | 9 | 1,172 | 66 | 91 | 15.7% | 16.7% | +1.0% |
| 170708_PD-W | 26 | 6,309 | 506 | 600 | 23.3% | 26.2% | +2.9% |
| 170714_PD-E | 4 | 6,804 | 79 | 118 | 0.9% | 1.5% | +0.6% |
| 201848_PD-W | 21 | 4,981 | 326 | 343 | 11.3% | 13.2% | +1.9% |
| 201855_PD-E | 12 | 5,043 | 116 | 153 | 13.5% | 21.0% | +7.5% |
| 073001_PD-W | 74 | 23,173 | 2,210 | 2,522 | 24.9% | 30.9% | +6.1% |
| 073006_PD-E | 71 | 23,144 | 2,132 | 2,501 | 21.8% | 27.7% | +5.9% |
| **TOTAL** | **230** | **72,594** | **5,842** | **6,772** | **17.7%** | **21.7%** | **+4.0%** |

## Regression Analysis

### Significant Regressions (≥5 LDUs worse)

| File | TX# | Time (ms) | LSM | v2 | Delta | Notes |
|------|-----|-----------|-----|-----|-------|-------|
| 155903_PD-E | 3 | 83,906 | 30.6% | 2.8% | -10 LDUs | **Investigate** |
| 170708_PD-W | 23 | 1,141,909 | 9.9% | 4.6% | -7 LDUs | |
| 073001_PD-W | 63 | 3,761,394 | 45.0% | 20.0% | -5 LDUs | |

### Minor Regressions (1-4 LDUs worse)

| File | TX# | Time (ms) | LSM | v2 | Delta |
|------|-----|-----------|-----|-----|-------|
| 155626_PD-W | 6 | 209,483 | 18.1% | 17.5% | -2 |
| 201848_PD-W | 7 | 184,036 | 9.3% | 9.3% | -1 |
| 201848_PD-W | 15 | 720,690 | 1.9% | 1.7% | -1 |
| 201848_PD-W | 18 | 937,177 | 73.6% | 72.7% | -1 |
| 073001_PD-W | 8 | 578,733 | 3.9% | 3.7% | -1 |
| 073001_PD-W | 31 | 2,140,519 | 68.8% | 65.6% | -1 |
| 073006_PD-E | 15 | 1,050,382 | 0.8% | 0.0% | -1 |
| 073006_PD-E | 18 | 1,099,674 | 1.0% | 0.7% | -4 |
| 073006_PD-E | 26 | 1,895,374 | 10.3% | 9.5% | -2 |
| 073006_PD-E | 38 | 2,476,964 | 53.4% | 53.1% | -1 |
| 073006_PD-E | 70 | 4,537,621 | 71.3% | 70.3% | -1 |

## Key Observations

### 1. Low Overall Decode Rates

Average decode rate across all files is only **17.7% (LSM)** to **21.7% (v2)** of expected LDUs. This indicates significant room for improvement.

**Possible causes:**
- Transmission boundary detection may be too aggressive (detecting noise as signal)
- Signal quality issues in source recordings
- Decoder cold-start issues at transmission beginnings
- PLL/sync acquisition delays

### 2. PD-E Files Perform Worse Than PD-W

| Channel | Avg LSM Score | Avg v2 Score |
|---------|---------------|--------------|
| PD-W (154.815 MHz) | 22.5% | 26.6% |
| PD-E (154.950 MHz) | 13.0% | 16.7% |

This suggests the PD-E channel may have:
- Weaker signal strength
- More interference
- Different usage patterns (longer transmissions with worse decode)

### 3. Transmission Length vs Decode Rate

Long transmissions (>100s) tend to have lower decode rates, suggesting:
- Energy detection may be merging multiple transmissions
- Sustained decode issues may compound over time

### 4. HDU/TDU Framing

From detailed reports, many transmissions lack proper HDU/TDU framing:
- Missing HDU: Cold-start sync acquisition delay
- Missing TDU: May indicate transmission boundary detection issue

## Important Finding: Timestamp Alignment Issue

Investigation of the TX#3 "regression" in 155903_PD-E revealed an important insight:

**When the isolated segment was run through the decoder:**
- LSM: 0 LDUs (vs 11 in full file)
- v2: 12 LDUs (vs 1 in full file)

**Conclusion:** The per-transmission "regressions" are not actual decode failures but **timestamp alignment shifts**. v2's improved sync acquisition causes it to decode the same audio content at slightly different file positions. The overall comparison (+25 LDUs for v2 on this file) is the meaningful metric.

**Implications:**
1. Per-transmission scoring is useful for identifying areas of concern, not definitive regressions
2. When investigating a "regression", always run the isolated segment to verify actual decode quality
3. v2's cold-start improvements may shift decode timing, which affects per-transmission scores but improves overall decode rate

## Recommended Optimizations

### Priority 1: Verify "Regressions" with Isolated Segments

When transmission scoring shows a regression, extract and verify with isolated decode:
```bash
./gradlew runTransmissionScoring -PbasebandFile=file.wav -Pnac=117 -PextractRegressions=true -PoutputDir=extracts
./gradlew runComparison -PbasebandFile=extracts/tx_regression.wav -Pnac=117
```

### Priority 2: Tune Energy Detection Thresholds

Current parameters may be too sensitive:
- `ENERGY_SILENCE_RATIO = 0.15` (15% of peak)
- `MIN_TRANSMISSION_MS = 180` (1 LDU)
- `MIN_GAP_MS = 500`

Consider:
- Increasing silence ratio to 0.20-0.25 to reduce false positives
- Increasing min transmission to 360ms (2 LDUs)
- Validating detected transmissions against decoded message timestamps

### Priority 3: Cold-Start Optimization

Many transmissions show 0% decode at the beginning, suggesting sync acquisition issues:
- Tune PLL parameters for faster lock
- Reduce sync pattern detection threshold
- Add pre-roll buffer to transmission detection

### Priority 4: Per-Transmission Analysis Tool

Create tool to:
1. Extract worst-performing transmissions
2. Run focused decode tests with different parameters
3. Generate detailed decode logs with symbol/bit error rates

## Next Steps

1. Implement slice extraction (T8) to isolate problem transmissions
2. Extract and analyze the TX#3 regression case
3. Add detailed logging mode to TransmissionScoringTest
4. Test energy detection threshold variations
5. Create parameter sweep tool to find optimal decoder settings
