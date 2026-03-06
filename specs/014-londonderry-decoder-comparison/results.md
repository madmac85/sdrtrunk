# Results: Londonderry FD Decoder Comparison

**Date**: 2026-02-18
**Branch**: `014-londonderry-decoder-comparison`
**Status**: COMPLETE — LSM v2 recommended, channel is CQPSK/simulcast

## Executive Summary

The Londonderry FD channel (156.1125 MHz, NAC 293) is a **CQPSK/simulcast** channel, not C4FM as currently configured in the playlist. LSM v2 is the clear winner, decoding 22.7% more LDUs than C4FM and 331% more than LSM. The playlist modulation setting should be changed from C4FM to CQPSK.

## Decoder Recommendation: LSM v2

| Metric                | C4FM     | LSM      | LSM v2        |
|-----------------------|----------|----------|---------------|
| **LDU Frames**        | 450      | 128      | **552**       |
| Audio (LDU x 180ms)  | 81.0s    | 23.0s    | **99.4s**     |
| Valid Messages        | 1,345    | 1,095    | **2,701**     |
| Total Messages        | 1,345    | 1,248    | 3,283         |
| Invalid Messages      | **0**    | 153      | 582           |
| Valid Rate            | **100%** | 87.7%    | 82.3%         |
| Error-Free Msg Rate   | **96.4%**| 14.1%    | 24.9%         |
| Est. BER              | **0.06%**| 2.09%    | 1.80%         |
| Sync Blocked (Guard)  | 45       | 200      | 3,205         |
| LDU Gaps (>500ms)     | 7        | 14       | **2**         |
| NID Success Rate      | **97.6%**| 2.4%     | 2.2%          |
| **Score**             | 2        | 0        | **5**         |

### Scoring Breakdown

- **LDU count (3 pts)**: LSM v2 (552) — clear winner
- **Valid messages (2 pts)**: LSM v2 (2,701) — clear winner
- **Lowest sync losses (1 pt)**: C4FM (6,619) — marginal win
- **Lowest BER (1 pt)**: C4FM (0.06%) — clear winner

**Final: LSM v2 = 5 pts, C4FM = 2 pts, LSM = 0 pts**

### Cross-Decoder Unique LDU Analysis

| Comparison              | Count |
|-------------------------|-------|
| C4FM unique vs LSM v2   | 2     |
| LSM v2 unique vs C4FM   | 508   |
| C4FM unique vs LSM      | 159   |
| LSM unique vs C4FM      | 107   |
| LSM unique vs LSM v2    | 4     |
| LSM v2 unique vs LSM    | 266   |

LSM v2 decodes essentially everything C4FM does (only 2 unique C4FM LDUs) plus 508 additional LDUs that C4FM misses entirely.

## Modulation Determination: CQPSK (Simulcast)

The comparison results conclusively indicate CQPSK/simulcast modulation:

1. **LSM v2 >> C4FM**: +22.7% LDU advantage. C4FM decoders struggle with simulcast interference patterns.
2. **LSM v1 collapses**: Only 128 LDUs (76.8% loss). LSM v1 lacks the cold-start resets and fade recovery that LSM v2 provides for simulcast.
3. **High fade recovery activity**: 2,568 fade recovery syncs in LSM v2, plus 35 boundary resets — both hallmarks of simulcast where signal fading occurs as mobile units move between coverage zones.
4. **Low NID success rate across all decoders**: 2.2-2.4% for LSM/LSM v2 (97.6% for C4FM is misleading — C4FM simply sees fewer sync candidates). Simulcast interference corrupts NID patterns.
5. **C4FM has high valid rate but low volume**: 100% valid rate with only 450 LDUs means C4FM only decodes the strongest/cleanest transmissions, missing the majority of simulcast-affected ones.

**Recommendation**: Change the playlist modulation setting from C4FM to CQPSK for the Londonderry FD channel.

## Gold Standard Baseline

| Parameter        | Value |
|------------------|-------|
| **Decoder**      | LSM v2 |
| **LDU Count**    | 552 |
| **Sample File**  | `_SAMPLES/Londonderry FD/20260218_080031_156112500_Municipalities_Londonderry_Londonderry-FD_14_baseband.wav` |
| **NAC**          | 293 |
| **Commit**       | `014-londonderry-decoder-comparison` branch |
| **Regression Command** | `./gradlew runDerryComparison -Pdir="_SAMPLES/Londonderry FD" -Pnac=293` |

## LSM v2 Diagnostics

| Metric              | Value  |
|---------------------|--------|
| Boundary resets     | 35     |
| Fade detections     | 2,597  |
| PLL resets          | 1,862  |
| Max PLL drift       | 1.047 rad (800 Hz) |
| Fallback syncs      | 119,846 |
| Fade recovery syncs | 2,568  |

The high fade detection count (2,597) and boundary reset count (35) are strong indicators of simulcast interference, where signals from multiple transmitter sites arrive with varying delay and phase.

## Channel Details

| Parameter    | Value |
|-------------|-------|
| **Frequency** | 156.1125 MHz |
| **NAC**       | 293 |
| **FCC Callsign** | KQO260 |
| **Licensee**  | Town of Londonderry |
| **Modulation** | CQPSK (empirically determined) |
| **Protocol**  | P25 Phase 1 |
| **RadioReference** | Fire Operations - Primary |
