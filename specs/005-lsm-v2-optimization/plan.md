# Implementation Plan: P25 LSM v2 Decoder Optimization

## Executive Summary

This plan outlines three optimization strategies to improve P25 LSM v2 decoder performance on missed transmissions, HDU detection, and TDU detection. The approach leverages the existing transmission scoring framework to measure improvements iteratively.

## Research Summary: P25 Conventional PTT Behavior

### P25 Phase 1 Frame Structure (from [TIA-102 and supporting documentation](https://www.taitradioacademy.com/topic/p25-channel-operation-1/))

- **HDU (Header Data Unit)**: Transmitted at start of voice call, identifies destination and encryption status
- **LDU1/LDU2 (Logical Data Units)**: 180ms each, form a 360ms superframe containing 18 IMBE voice codewords
- **TDU (Terminator Data Unit)**: Signals PTT release and end of transmission
- **NID (Network ID)**: 64-bit field (12-bit NAC + 4-bit DUID + BCH parity) follows every sync pattern
- **Symbol Rate**: 4800 baud, each LDU = ~327ms at wire level

### Cold-Start Acquisition Challenges

On conventional PTT channels, the transmitter carrier turns on and off with each transmission. The decoder must:
1. Detect carrier presence (energy rise)
2. Acquire symbol timing (Gardner TED)
3. Lock PLL to carrier frequency offset
4. Detect sync pattern (24 dibits)
5. Decode NID via BCH error correction
6. Begin message assembly

The first HDU may arrive within 20-50ms of carrier-on. With PLL acquisition taking 24 symbols (~5ms) and timing acquisition taking 4 symbols (~0.8ms), there's a tight window to capture the HDU.

### Key Timing Parameters

| Parameter | Value | Notes |
|-----------|-------|-------|
| Symbol rate | 4800 baud | 208μs per symbol |
| Sync pattern | 24 dibits (48 bits) | 5ms duration |
| NID | 32 dibits (64 bits) | 6.7ms duration |
| HDU | 648 bits total | ~135ms duration |
| LDU | 1568 bits total | ~327ms duration |
| TDU | 28 bits | ~5.8ms duration |

## Optimization Strategies

### Strategy 1: Enhanced Sync Detection with Adaptive Threshold

**Problem**: Fixed sync correlation threshold of 60 may miss weak sync patterns at transmission boundaries.

**Solution**: Implement adaptive sync threshold based on signal energy level. When signal energy rises but no sync is detected, progressively lower the threshold.

**Implementation**:
- Track signal energy EMA alongside sync detection
- Implement two-tier sync detection: primary threshold (60) and fallback threshold (45)
- Fallback threshold activates only when:
  1. Signal energy > 50% of peak (strong signal present)
  2. No sync detected for > 48 symbols (one sync pattern length) after energy rise
- Add diagnostic counter for fallback sync detections

**Files Modified**:
- `P25P1MessageFramer.java`: Add secondary sync threshold and energy-aware logic
- `P25P1DecoderLSMv2.java`: Pass signal energy state to framer

**Expected Impact**: +15-25% HDU detection on transmissions with weak initial sync

### Strategy 2: Transmission Boundary-Triggered Sync Recovery

**Problem**: When energy boundary detection triggers cold-start reset, the decoder may miss the first sync if it arrives before the reset completes or if timing/PLL haven't stabilized.

**Solution**: After boundary detection, actively search for sync using hard sync detection in parallel with soft sync detection for the first 100ms of a new transmission.

**Implementation**:
- Add `mBoundaryRecoveryActive` flag set true for 480 symbols (~100ms) after boundary reset
- During recovery period, run both soft and hard sync detection
- Hard sync detector already allows 4 bit errors (more tolerant)
- If hard sync triggers during recovery but soft doesn't, still process NID
- Add `mRecoverySyncCount` diagnostic counter

**Files Modified**:
- `P25P1DecoderLSMv2.java`: Set recovery flag on boundary detection
- `P25P1MessageFramer.java`: Add hard sync fallback during recovery window
- `P25P1DemodulatorLSMv2.java`: Track recovery state

**Expected Impact**: +10-20% HDU detection on transmissions where initial sync is corrupted

### Strategy 3: End-of-Transmission TDU Recovery

**Problem**: TDU at transmission end is frequently missed because:
1. Signal energy is fading as transmitter ramps down
2. Decoder may still be assembling previous LDU when TDU arrives
3. TDU is very short (28 bits) - easy to miss if timing drifts

**Solution**: Implement predictive TDU detection based on energy fade profile.

**Implementation**:
- Track energy derivative (rate of change) alongside absolute energy
- When energy drops by > 50% within 50ms AND current message assembly is near complete:
  1. Force-complete any in-progress message assembly
  2. Lower sync threshold to 40 for next 100ms
  3. If sync detected within this window but NID fails, attempt TDU-specific recovery
- TDU-specific recovery: If assembled bits < 50 and previous DUID was LDU2, assume TDU

**Files Modified**:
- `P25P1DecoderLSMv2.java`: Add energy fade detection
- `P25P1MessageFramer.java`: Add TDU-specific assembly recovery
- `P25P1MessageAssembler.java`: Add method to identify probable TDU fragments

**Expected Impact**: +10-15% TDU detection on transmissions with signal fade

## Diagnostic Enhancements

### New Metrics to Track

1. **Sync Detection by Type**:
   - Primary soft sync (threshold 60)
   - Fallback soft sync (threshold 45)
   - Hard sync during recovery
   - TDU recovery sync

2. **Boundary Detection**:
   - Energy rise detections
   - Energy fade detections
   - Recovery window activations

3. **NID Processing**:
   - Success rate by threshold type
   - NAC-assisted corrections
   - Failed NID with subsequent message recovery

### Enhanced Test Output

Update `TransmissionScoringTest.java` to report:
- HDU detection rate (%)
- TDU detection rate (%)
- LDU count comparison
- Sync detection breakdown by type

## Implementation Order

1. **Phase 1: Baseline Measurement** (existing infrastructure)
   - Run scoring on all 8 samples with baseline v2
   - Document HDU/TDU detection rates per sample

2. **Phase 2: Strategy 1 - Adaptive Sync** (lowest risk)
   - Implement adaptive sync threshold
   - Run scoring, compare HDU rates
   - Tune threshold parameters if needed

3. **Phase 3: Strategy 2 - Boundary Recovery** (medium risk)
   - Implement boundary recovery window
   - Run scoring, compare overall improvement
   - Verify no regression on LDU count

4. **Phase 4: Strategy 3 - TDU Recovery** (highest complexity)
   - Implement energy fade detection
   - Add TDU-specific recovery logic
   - Run full scoring suite

5. **Phase 5: Final Report**
   - Generate comparative report: LSM vs v2 baseline vs v2 optimized
   - Create executive summary with delta tables
   - Document each strategy's contribution

## Risk Mitigation

- Each strategy is independently reversible via flag
- Run full 8-sample regression after each strategy
- No modification to core demodulation math (only thresholds and state machines)
- Preserve all existing diagnostic counters

## Success Criteria Mapping

| Spec Criteria | Strategy | Target |
|---------------|----------|--------|
| SC-001: 30% missed TX recovery | All | 30% of 0% TX achieve >10% |
| SC-002: 15% HDU improvement | 1, 2 | +15% HDU detection |
| SC-003: 10% TDU improvement | 3 | +10% TDU detection |
| SC-004: No LDU regression | All | LDU count >= baseline |
| SC-005: Executive summary | Phase 5 | Report with deltas |
| SC-006: 3 strategies documented | All | This plan |
| SC-007: P25 research documented | Above | Research summary |
| SC-008: All 8 samples tested | All | Required |

## Files to Create/Modify

### New Files
- `specs/005-lsm-v2-optimization/research.md` - P25 conventional PTT research
- `specs/005-lsm-v2-optimization/report.md` - Final optimization report

### Modified Files
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderLSMv2.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DemodulatorLSMv2.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageFramer.java`
- `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1MessageAssembler.java`
- `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScoringTest.java`
- `src/test/java/io/github/dsheirer/module/decode/p25/phase1/TransmissionScorer.java`
