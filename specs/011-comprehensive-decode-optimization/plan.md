# Implementation Plan: P25 LSM v2 Comprehensive Decode Optimization

**Feature Branch**: `011-comprehensive-decode-optimization`
**Created**: 2026-02-02
**Spec**: [spec.md](./spec.md)
**Analysis**: [analysis_report.md](./analysis_report.md)

## Research Summary

### Key Finding: NID Decode Failure Is The Critical Bottleneck

Analysis of the ~1 hour Roc West sample revealed:
- **72.4% NID decode failure rate** - the majority of sync detections cannot extract message type
- Only **13.9% of sync detections** result in successful message decode
- **35.9% of transmissions** produce zero decoded LDUs
- Overall LDU decode rate: **9.3%** of expected

The current v2 decoder already improves LDU recovery by +16.6% over LSM, but significant gains remain possible by addressing the NID bottleneck and implementing error concealment.

## Architecture Overview

### Current Decode Pipeline

```
IQ Samples → Demodulator (P25P1DemodulatorLSMv2)
                  ↓
           Dibit Stream (9600 symbols/sec)
                  ↓
           Message Framer (P25P1MessageFramer)
                  ↓ Sync Detection (48-bit pattern)
                  ↓ NID Decode (BCH 63,16,23 → NAC + DUID)
                  ↓ Message Assembly (based on DUID)
                  ↓
           Message (LDU1, LDU2, HDU, TDU, etc.)
                  ↓
           P25P1DecoderState
                  ↓
           P25P1AudioModule → JMBE Codec → PCM Audio
```

### Problem Analysis by Layer

| Layer | Issue | Impact | Optimization Opportunity |
|-------|-------|--------|-------------------------|
| **Sync** | 54.4% fallback sync rate | Weak sync confidence | Adaptive thresholds |
| **NID** | 72.4% decode failure | Cannot determine message type | Soft-decision decoding, known NAC |
| **Message** | Reed-Solomon not recovering | Lost LDUs | Iterative/turbo decoding |
| **Audio** | IMBE frame corruption | Artifacts | Frame error detection/concealment |

## Implementation Plan

### Phase 1: NID Enhancement (Highest Impact)

#### Component 1.1: Soft-Decision NID Decoding

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`
- `src/main/java/io/github/dsheirer/edac/BCH_63_16_23.java` (new soft decoder)

**Current State**:
- NID uses BCH(63,16,23) code with hard-decision decode
- Can correct up to 11 bit errors (theoretical)
- In practice, fails frequently with marginal signals

**Enhancement**:
1. Capture soft symbol values (correlation confidence) during sync detection
2. Implement soft-decision BCH decoding using Chase algorithm or similar
3. Weight bit reliability based on symbol amplitude
4. Expected improvement: 20-40% reduction in NID failures

**Algorithm**:
```
For each detected sync:
  1. Extract soft symbol values for 64 NID bits
  2. Identify least reliable bits (lowest amplitude)
  3. Try multiple bit flip combinations on unreliable bits
  4. Select decode with lowest error syndrome
```

#### Component 1.2: Known-NAC Optimization

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/DecodeConfigP25Phase1.java`

**Current State**:
- NAC can be configured but is only used after successful decode
- NID must decode both NAC (10 bits) and DUID (4 bits) = 14 information bits

**Enhancement**:
1. When NAC is configured (non-zero), use it as known constraint
2. Only need to decode DUID (4 bits = 16 possibilities)
3. Reduces decode complexity and increases success rate
4. Try all 16 DUID values and select one with valid message structure

**Expected Impact**:
- Configured channels: 30-50% reduction in NID failures
- Auto-detect channels: No change (fall back to full decode)

### Phase 2: Audio Frame Error Detection and Concealment

#### Component 2.1: IMBE Frame Validator

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/audio/P25P1AudioModule.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/audio/IMBEFrameValidator.java` (new)

**Current State**:
- IMBE frames are passed directly to JMBE codec
- No validation before decode
- Corrupted frames produce audio artifacts

**Enhancement**:
1. Implement pre-decode frame validation:
   - Hamming(10,6,3) check on 88 pitch/gain bits
   - Energy consistency check (adjacent frames shouldn't vary wildly)
   - Bit pattern validity (reserved bits, range checks)
2. Mark suspicious frames for concealment
3. Collect frame error metrics

**Frame Error Detection Criteria**:
```
Frame marked as suspicious if:
  - Hamming syndrome indicates uncorrectable error
  - Energy delta from previous frame > threshold
  - Known bad bit patterns detected
```

#### Component 2.2: Audio Concealment Strategies

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/audio/P25P1AudioModule.java`
- `src/main/java/io/github/dsheirer/audio/codec/mbe/ImbeAudioModule.java`

**Concealment Methods** (in priority order):
1. **Frame Repetition**: Repeat last good frame (simple, effective for single errors)
2. **Silence Insertion**: Insert calibrated silence (better than garbage audio)
3. **Linear Interpolation**: Blend between good frames (more complex)

**Implementation**:
```java
if (frameValidator.isSuspicious(imbeFrame)) {
    switch (concealmentStrategy) {
        case REPEAT_LAST:
            audio = getAudioCodec().getAudio(lastGoodFrame);
            break;
        case SILENCE:
            audio = new float[160]; // 20ms of silence
            break;
        case INTERPOLATE:
            audio = interpolateFromContext();
            break;
    }
    stats.concealedFrames++;
} else {
    audio = getAudioCodec().getAudio(imbeFrame);
    lastGoodFrame = imbeFrame;
}
```

### Phase 3: Adaptive Sync and Recovery

#### Component 3.1: Environment-Adaptive Sync Threshold

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`

**Current State**:
- Fixed sync threshold (e.g., ≤6 bit errors)
- Fallback to relaxed threshold when primary fails

**Enhancement**:
1. Track recent sync success rate (sliding window)
2. Adjust threshold based on observed environment:
   - High success → tighten threshold (reduce false positives)
   - Low success → relax threshold (increase decode attempts)
3. Bounded range to prevent runaway adjustment

**Algorithm**:
```java
double successRate = recentSuccesses / recentAttempts;
if (successRate > 0.8) {
    threshold = Math.max(threshold - 1, MIN_THRESHOLD);
} else if (successRate < 0.2) {
    threshold = Math.min(threshold + 1, MAX_THRESHOLD);
}
```

#### Component 3.2: Enhanced Fade Recovery

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DemodulatorLSMv2.java`

**Current State**:
- Fade recovery triggers at 3.3% of syncs
- Limited effectiveness (only recovers ~5% of fade losses)

**Enhancement**:
1. Extend fade detection window (currently too short for long fades)
2. Add signal energy monitoring to detect fade onset
3. Pre-position sync search at expected frame boundaries
4. Implement "catch-up" mode after extended fade

### Phase 4: Channel Configuration for Voice-Only Operation

#### Component 4.1: Encryption Detection Bypass

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/DecodeConfigP25Phase1.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/audio/P25P1AudioModule.java`

**Current State**:
- Encrypted call detection delays audio until LDU2 received
- False encryption detections cause audio loss

**Enhancement**:
1. Add `disableEncryptionDetection` configuration flag
2. When enabled, assume all calls are unencrypted
3. Begin audio processing immediately on LDU1

**Use Case**: Dedicated voice channels with no encryption capability

#### Component 4.2: Control Channel Detection Bypass

**Files**:
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/DecodeConfigP25Phase1.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderState.java`

**Enhancement**:
1. Add `disableControlChannelDetection` configuration flag
2. When enabled, never transition to CONTROL state
3. Prevents false control channel detection from decode errors

### Phase 5: Research and Metrics Infrastructure

#### Component 5.1: Comprehensive Quality Metrics

**Files**:
- `src/test/java/io/github/dsheirer/module/decode/p25/phase1/DecodeQualityMetrics.java` (new)
- `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScoringTest.java`

**Metrics to Track**:
- Sync detection rate (per signal level)
- NID decode success rate (overall and by DUID type)
- Bit error rate (pre and post correction)
- Audio frame error rate
- Concealment events
- Call continuity metrics

#### Component 5.2: Batch Analysis Tool

**Files**:
- `src/test/java/io/github/dsheirer/module/decode/p25/phase1/BatchAnalysis.java` (new)

**Features**:
- Process multiple recordings
- Generate comparative reports
- Track optimization impact over time
- Export metrics to CSV for analysis

## Implementation Priority

| Phase | Component | Priority | Expected Impact | Effort |
|-------|-----------|----------|-----------------|--------|
| 1.2 | Known-NAC Optimization | P1 | +30-50% NID success | Low |
| 2.1 | IMBE Frame Validator | P1 | Reduce artifacts 80% | Medium |
| 2.2 | Audio Concealment | P1 | Eliminate garbage audio | Medium |
| 1.1 | Soft-Decision NID | P2 | +20-40% NID success | High |
| 4.1 | Encryption Bypass | P2 | Faster audio start | Low |
| 4.2 | Control Bypass | P2 | Reduce false states | Low |
| 3.1 | Adaptive Sync | P3 | +5-10% sync success | Medium |
| 3.2 | Enhanced Fade | P3 | +10% fade recovery | Medium |
| 5.x | Metrics Infrastructure | P3 | Research enablement | Medium |

## File Changes Summary

| File | Change Type | Phase |
|------|-------------|-------|
| `P25P1MessageFramer.java` | MODIFY | 1, 3 |
| `BCH_63_16_23_Soft.java` | NEW | 1 |
| `DecodeConfigP25Phase1.java` | MODIFY | 1, 4 |
| `P25P1AudioModule.java` | MODIFY | 2, 4 |
| `IMBEFrameValidator.java` | NEW | 2 |
| `P25P1DemodulatorLSMv2.java` | MODIFY | 3 |
| `P25P1DecoderState.java` | MODIFY | 4 |
| `DecodeQualityMetrics.java` | NEW | 5 |
| `BatchAnalysis.java` | NEW | 5 |

## Success Criteria Mapping

| Spec SC | Metric | Target | Phase |
|---------|--------|--------|-------|
| SC-001 | Audio artifact rate | -80% | 2 |
| SC-002 | Call fragmentation ratio | <1.1 | (existing) |
| SC-003 | LDU recovery rate | >90% theoretical | 1, 3 |
| SC-004 | False encryption detections | 0 | 4 |
| SC-005 | Research report | Complete | 5 |
| SC-006 | Roc West clean audio | >95% | 2 |
| SC-007 | No regression | Verified | All |

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Soft-decision NID complexity | High effort | Start with known-NAC optimization (low effort, high value) |
| Audio concealment sounds unnatural | User experience | Test with real recordings, allow user configuration |
| Adaptive thresholds become unstable | Regression | Bounded adjustment, extensive testing |
| Configuration explosion | UX complexity | Sensible defaults, advanced options hidden |

## Testing Strategy

1. **Unit Tests**: Each component tested in isolation
2. **Integration Tests**: Full decode pipeline with sample recordings
3. **Regression Tests**: No reduction in existing metrics
4. **A/B Comparison**: Before/after with multiple recordings
5. **User Acceptance**: Listening tests for audio quality
