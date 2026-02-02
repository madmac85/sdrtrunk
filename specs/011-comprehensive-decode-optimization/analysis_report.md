# P25 LSM v2 Error Rate Assessment Report

**Date**: 2026-02-02
**Samples Analyzed**: 4 baseband recordings from Rockingham County PD West (154.815 MHz)

## Executive Summary

The LSM v2 decoder shows **+16.6% improvement in LDU recovery** over the original LSM decoder on the ~1 hour Roc West sample, with **+446 additional LDUs** decoded. However, the overall decode rate remains low at **9.3%** of expected LDUs, indicating significant room for improvement in challenging signal conditions.

## Sample Analysis Summary

| Sample | Duration | Transmissions | Expected LDUs | v2 LDUs | v2 Decode Rate | v2 Improvement |
|--------|----------|---------------|---------------|---------|----------------|----------------|
| Sample 1 (7 min) | 414s | 13 | 1,968 | 445 | 22.5% | +9.4% over LSM |
| Sample 2 (21 min) | 1,258s | 26+ | 5,000+ | 1,200+ | 18.8% | +8.9% over LSM |
| Sample 3 (16 min) | 983s | 17+ | 3,000+ | 500+ | 15.0% | +7.1% over LSM |
| **Roc West (111 min)** | **6,672s** | **153** | **33,854** | **3,138** | **9.3%** | **+16.6% over LSM** |

## Error Metrics Deep Dive (Roc West Sample)

### Message-Level Statistics

| Metric | LSM | v2 | Delta | Notes |
|--------|-----|-----|-------|-------|
| Total Messages | 18,538 | 22,433 | +3,895 | v2 decodes 21% more messages |
| Valid Messages | 18,424 | 22,205 | +3,781 | 99.4% vs 99.0% validity |
| Invalid Messages | 114 | 228 | +114 | Higher invalid count expected with more decode attempts |
| Messages w/ Bit Errors | 1,455 | 1,811 | +356 | More corrections attempted |
| Total Bits Corrected | 7,502 | 10,248 | +2,746 | ~37% more error correction |
| Max Errors/Message | 20 | 20 | +0 | Same ceiling on correctability |
| Est. BER | 0.18% | 0.21% | +0.03% | Slightly higher BER from marginal signal recovery |
| Error-Free Message Rate | 92.1% | 91.8% | -0.3% | Slightly lower due to marginal signal recovery |

### Sync/NID Diagnostics (v2 only)

| Metric | Count | Percentage | Interpretation |
|--------|-------|------------|----------------|
| Sync Detections | 161,475 | 100% | Total sync pattern matches |
| NID Decode Success | 22,377 | 13.9% | Successfully decoded message type |
| NID Decode Fail | 116,967 | 72.4% | Failed to decode message type |
| Fallback Sync | 87,903 | 54.4% | Used relaxed sync matching |
| Boundary Recovery | 58 | 0.0% | Recovered at frame boundaries |
| Fade Recovery | 5,397 | 3.3% | Recovered after signal fade |

### Key Observations

1. **High NID Failure Rate (72.4%)**: The majority of sync detections fail to decode a valid NID. This indicates:
   - Many false sync pattern matches in noise
   - Signal quality insufficient for reliable NID decoding after sync
   - The 10-bit NAC in NID requires strong signal for error correction

2. **Fallback Sync Dominance (54.4%)**: Over half of all syncs use relaxed matching, indicating marginal signal conditions are the norm, not the exception.

3. **Low Boundary/Fade Recovery Utilization**: These optimizations trigger rarely, suggesting:
   - Boundary recovery (0.0%): Few messages are lost at exact frame boundaries
   - Fade recovery (3.3%): Fades are either too long or too severe for recovery

4. **35.9% of Transmissions Missed Entirely**: Over one-third of detected transmissions produce zero decoded LDUs from either decoder, indicating:
   - Signal levels below minimum decode threshold
   - Extended interference or severe multipath
   - Potential transmission detection false positives from energy thresholding

## Transmission-Level Analysis

### HDU/TDU Detection Rates

| Metric | v2 Count | Percentage | Impact |
|--------|----------|------------|--------|
| With HDU | 37 | 24.2% | Call headers rarely decoded |
| With TDU | 74 | 48.4% | Terminators decoded half the time |
| Complete (HDU+TDU) | 31 | 20.3% | Only 1 in 5 calls has proper framing |

**Impact**: Missing HDUs prevent proper call metadata (talkgroup, encryption status). Missing TDUs cause delayed call termination detection.

### v2 Regressions

13 transmissions showed v2 performing worse than LSM, totaling ~48 LDUs lost. The largest regression was TX#92 with 12 fewer LDUs (71.6% vs 81.9%). Possible causes:
- Sync algorithm timing differences
- PLL tracking divergence on marginal signals
- Different error correction thresholds

## Root Cause Analysis

### Why 90%+ of LDUs Are Lost

1. **Weak Signal Strength**: The channel appears to have marginal signal conditions for much of the recording. The transmission mapper detected 153 transmissions but 55 (35.9%) produced zero decoded LDUs.

2. **NID Bottleneck**: The 72.4% NID decode failure rate is the critical bottleneck. Even when sync is detected, the message type (DUID) cannot be reliably extracted.

3. **Extended Signal Fades**: Many transmissions span minutes (longest: 5.5 minutes / TX#87) with very low decode rates (0.4%), suggesting persistent signal degradation rather than brief fades.

4. **Possible Interference**: The consistent pattern of low decode rates across many transmissions suggests environmental factors (interference, multipath) rather than decoder issues.

### Signal Quality Distribution

Based on the transmission scores:
- **0-10% decode**: 67 transmissions (43.8%) - Very weak/no signal
- **10-30% decode**: 23 transmissions (15.0%) - Marginal
- **30-60% decode**: 34 transmissions (22.2%) - Moderate
- **60%+ decode**: 29 transmissions (19.0%) - Good

## Recommendations for Spec 011

### High Priority

1. **NID Error Correction Enhancement**: The 72.4% NID failure rate is the biggest single opportunity. Research:
   - Soft-decision NID decoding using raw correlation values
   - Using known NAC (configurable) to reduce unknown bits from 10 to 0
   - Multi-hypothesis NID decoding (try multiple DUID values)

2. **Signal Quality Gating**: Don't attempt decode when SNR is too low. This reduces:
   - False sync matches
   - Wasted CPU on undecodable signals
   - Invalid message generation

3. **Adaptive Sync Threshold**: The 54.4% fallback sync rate suggests the primary threshold is too strict for this environment. Consider:
   - Environment-adaptive thresholds based on recent decode success
   - Signal-strength-weighted sync confidence

### Medium Priority

4. **Frame Interpolation for Missed LDUs**: When LDU count is below expected within a transmission, consider:
   - Silence insertion for gaps
   - Previous frame repetition for brief gaps
   - Audio interpolation for single frame drops

5. **IMBE Frame Error Detection**: Add per-frame quality metrics to:
   - Identify corrupted audio frames before JMBE decode
   - Enable frame-level concealment instead of playing artifacts

### Lower Priority

6. **Multi-Tuner Diversity**: Could help with:
   - Multipath mitigation (different antenna placement)
   - Burst error reduction (combine streams)
   - However, requires significant architecture changes

7. **PLL Optimization**: The existing PLL shows reasonable stability but could benefit from:
   - Faster lock acquisition after fades
   - Better drift tracking in marginal conditions

## Test Data Artifacts

All analysis was performed on:
- `_SAMPLES/20260124_*.wav` - Original test samples
- `_SAMPLES/additional_samples/20260202_*.wav` - New Roc West ~1hr sample

The large Roc West sample provides excellent test coverage for:
- Extended monitoring sessions
- Real-world signal conditions
- Edge cases (very short/long transmissions, severe fades)
