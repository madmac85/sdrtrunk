# Tasks: P25 LSM v2 Comprehensive Decode Optimization

**Feature Branch**: `011-comprehensive-decode-optimization`
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

## Phase 1: NID Enhancement

### Task 1.1: Known-NAC NID Optimization
**Priority**: P1 | **Effort**: Low | **Impact**: High | **Status**: ALREADY IMPLEMENTED

- [x] 1.1.1 NACTracker has `setConfiguredNAC()` method - Already exists
- [x] 1.1.2 BCH decoder uses configured NAC for error correction - Already implemented
- [x] 1.1.3 DecodeConfigP25Phase1 has `configuredNAC` property - Already exists
- [x] 1.1.4 DecoderFactory wires config to decoder - Already implemented
- [ ] 1.1.5 Write unit tests for known-NAC decode path - Needs implementation
- [ ] 1.1.6 Run regression tests on all sample files - Needs validation

**Note**: This optimization was already implemented in previous work. The configured NAC is used by the BCH decoder to assist with NID error correction when the first decode attempt fails.

**Acceptance Criteria**:
- Configured NAC channels show improved NID decode rate
- No regression on auto-detect channels

### Task 1.2: Soft-Decision NID Decoding (Future)
**Priority**: P2 | **Effort**: High | **Impact**: Medium

- [ ] 1.2.1 Research Chase algorithm for BCH soft decoding
- [ ] 1.2.2 Capture soft symbol values during sync correlation
- [ ] 1.2.3 Implement BCH_63_16_23_Soft decoder class
- [ ] 1.2.4 Integrate soft decoder into message framer
- [ ] 1.2.5 Benchmark performance impact
- [ ] 1.2.6 A/B test against hard-decision decoder

---

## Phase 2: Audio Frame Error Detection and Concealment

### Task 2.1: IMBE Frame Validator
**Priority**: P1 | **Effort**: Medium | **Impact**: High | **Status**: PARTIAL

- [x] 2.1.1 Create IMBEFrameValidator class - Implemented as methods in P25P1AudioModule
- [ ] 2.1.2 Implement Hamming(10,6,3) syndrome check for pitch/gain bits - Deferred
- [x] 2.1.3 Implement energy consistency check (frame-to-frame delta)
- [ ] 2.1.4 Add bit pattern validity checks (reserved bits, ranges) - Deferred
- [x] 2.1.5 Return suspicion score (0.0 = confident good, 1.0 = definitely bad) - Boolean impl
- [ ] 2.1.6 Write unit tests with known good/bad frames - Needs listening tests

**Note**: Energy-based validation implemented. Hamming/bit-level checks deferred pending evaluation of energy-based approach effectiveness.

**Acceptance Criteria**:
- Validator correctly identifies 90%+ of corrupted frames
- False positive rate < 5% on clean audio

### Task 2.2: Audio Concealment Implementation
**Priority**: P1 | **Effort**: Medium | **Impact**: High | **Status**: COMPLETE

- [x] 2.2.1 Add `lastGoodFrame` tracking to P25P1AudioModule
- [x] 2.2.2 Implement REPEAT_LAST concealment strategy
- [x] 2.2.3 Implement SILENCE concealment strategy
- [x] 2.2.4 Add concealment statistics tracking
- [x] 2.2.5 Add configuration for concealment strategy selection
- [ ] 2.2.6 Test with recordings containing known artifacts - Needs user testing
- [ ] 2.2.7 Conduct listening tests to verify improvement - Needs user testing

**Acceptance Criteria**:
- Audio artifacts reduced by 80% measured by listening evaluation
- No audible "digital garbage" on decode errors

---

## Phase 3: Adaptive Sync and Recovery

### Task 3.1: Environment-Adaptive Sync Threshold
**Priority**: P3 | **Effort**: Medium | **Impact**: Low

- [ ] 3.1.1 Add sync success tracking (sliding window of last N attempts)
- [ ] 3.1.2 Implement threshold adjustment algorithm
- [ ] 3.1.3 Add bounds checking (MIN_THRESHOLD to MAX_THRESHOLD)
- [ ] 3.1.4 Add diagnostic logging for threshold changes
- [ ] 3.1.5 Test on samples with varying signal quality
- [ ] 3.1.6 Verify no runaway threshold adjustment

### Task 3.2: Enhanced Fade Recovery
**Priority**: P3 | **Effort**: Medium | **Impact**: Low

- [ ] 3.2.1 Extend fade detection window configuration
- [ ] 3.2.2 Add signal energy fade onset detection
- [ ] 3.2.3 Implement frame boundary pre-positioning
- [ ] 3.2.4 Add catch-up mode after extended fades
- [ ] 3.2.5 Test fade recovery improvements on sample files

---

## Phase 4: Channel Configuration

### Task 4.1: Encryption Detection Bypass
**Priority**: P2 | **Effort**: Low | **Impact**: Medium | **Status**: COMPLETE

- [x] 4.1.1 Add `ignoreEncryptionState` to DecodeConfigP25Phase1
- [x] 4.1.2 Add getter/setter and XML persistence
- [x] 4.1.3 Modify P25P1AudioModule to bypass encryption wait when disabled
- [x] 4.1.4 Test that audio starts immediately on LDU1
- [x] 4.1.5 Verify no false encryption events on voice-only channel

**Acceptance Criteria**:
- Zero false encryption detections on configured voice-only channels
- Audio begins processing on first LDU1 instead of waiting for LDU2

### Task 4.2: Control Channel Detection Bypass
**Priority**: P2 | **Effort**: Low | **Impact**: Medium | **Status**: COMPLETE

- [x] 4.2.1 Add `ignoreControlChannelState` to DecodeConfigP25Phase1
- [x] 4.2.2 Add getter/setter and XML persistence
- [x] 4.2.3 Modify P25P1DecoderState to never enter CONTROL state when disabled
- [x] 4.2.4 Test that no control channel transitions occur
- [x] 4.2.5 Verify normal call processing unaffected

### Task 4.3: Channel Configuration UI
**Priority**: P2 | **Effort**: Low | **Impact**: Medium | **Status**: COMPLETE

- [x] 4.3.1 Add ToggleSwitch for ignoreEncryptionState to P25P1ConfigurationEditor
- [x] 4.3.2 Add ToggleSwitch for ignoreControlChannelState to P25P1ConfigurationEditor
- [x] 4.3.3 Add ComboBox for audioConcealment strategy selection
- [x] 4.3.4 Show/hide LSM v2 options based on modulation selection
- [x] 4.3.5 Update setDecoderConfiguration() to load new settings
- [x] 4.3.6 Update saveDecoderConfiguration() to save new settings
- [x] 4.3.7 Verify backwards compatibility with existing XML configs

**Acceptance Criteria**:
- UI controls appear only when LSM v2 modulation is selected
- Settings persist correctly through save/load cycle
- Existing channel configs without new fields load without error

---

## Phase 5: Research and Metrics Infrastructure

### Task 5.1: Decode Quality Metrics
**Priority**: P3 | **Effort**: Medium | **Impact**: Research

- [ ] 5.1.1 Create DecodeQualityMetrics class
- [ ] 5.1.2 Track sync detection rate by signal level
- [ ] 5.1.3 Track NID decode success rate by DUID type
- [ ] 5.1.4 Track bit error rate (pre/post correction)
- [ ] 5.1.5 Track audio frame error rate
- [ ] 5.1.6 Track concealment events
- [ ] 5.1.7 Add metrics export to JSON/CSV

### Task 5.2: Batch Analysis Tool
**Priority**: P3 | **Effort**: Medium | **Impact**: Research

- [ ] 5.2.1 Create BatchAnalysis main class
- [ ] 5.2.2 Implement directory scanning for baseband files
- [ ] 5.2.3 Process multiple files sequentially
- [ ] 5.2.4 Generate comparative summary report
- [ ] 5.2.5 Export metrics to CSV for external analysis
- [ ] 5.2.6 Add progress reporting for large batches

---

## Integration and Testing

### Task 6.1: Regression Testing
**Priority**: P1 | **Effort**: Low | **Impact**: Critical

- [ ] 6.1.1 Run LSMv2ComparisonTest on all 8 original samples
- [ ] 6.1.2 Verify no reduction in LDU counts
- [ ] 6.1.3 Verify no increase in sync losses
- [ ] 6.1.4 Run TransmissionScoringTest on Roc West sample
- [ ] 6.1.5 Document before/after metrics

### Task 6.2: User Acceptance Testing
**Priority**: P1 | **Effort**: Medium | **Impact**: High

- [ ] 6.2.1 Create listening test protocol
- [ ] 6.2.2 Select 10 representative transmission segments
- [ ] 6.2.3 Generate before/after audio samples
- [ ] 6.2.4 Conduct blind listening evaluation
- [ ] 6.2.5 Document quality improvement ratings

---

## Progress Summary

| Phase | Tasks | Completed | Status |
|-------|-------|-----------|--------|
| Phase 1: NID | 2 | 1 | 50% (1.1 already done, 1.2 deferred) |
| Phase 2: Audio | 2 | 1.5 | 75% Complete |
| Phase 3: Sync | 2 | 0 | Not Started |
| Phase 4: Config | 3 | 3 | **Complete** (incl. UI) |
| Phase 5: Metrics | 2 | 0 | Not Started |
| Integration | 2 | 0 | Not Started |
| **Total** | **13** | **5.5** | **42%** |

## Recommended Implementation Order

1. **Task 4.1**: Encryption Detection Bypass (quick win for spec channel)
2. **Task 4.2**: Control Channel Detection Bypass (quick win)
3. **Task 2.1**: IMBE Frame Validator (foundation for concealment)
4. **Task 2.2**: Audio Concealment (immediate audio quality improvement)
5. **Task 1.1**: Known-NAC NID Optimization (major decode improvement)
6. **Task 6.1**: Regression Testing (verify changes)
7. **Task 6.2**: User Acceptance Testing (validate quality improvement)
8. **Task 3.x**: Adaptive Sync (incremental improvement)
9. **Task 5.x**: Metrics Infrastructure (ongoing research support)
10. **Task 1.2**: Soft-Decision NID (future enhancement)
