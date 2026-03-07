# Londonderry FD Improvement Plan

## Current Baseline (NAC=659, CQPSK_V2)
| Metric | Old (Feb 18) | New (Mar 6) |
|--------|-------------|-------------|
| LDUs | 569 | 503 |
| STT Words | 874 | 814 |
| Audio Segments | 345 | 377 |
| Avg Segment Length | 0.30s | 0.24s |

## Root Cause Analysis

### Problem 1: Extreme Audio Fragmentation (avg 0.24-0.30s segments)
The audio is broken into 345-377 tiny segments averaging only 0.24-0.30s each. For comparison, ROC W averages 1.2-1.5s segments. This means the audio output sounds choppy and discontinuous.

**Root cause**: Simulcast multipath causes frequent NID (Network ID) corruption. When the NID is corrupted:
1. The LDU cannot be validated (NAC mismatch or BCH failure)
2. No valid LDU is produced for the audio module
3. After 180ms holdover (`audioHoldoverMs=180`), the decoder state transitions to SQUELCH
4. The audio segment is closed
5. When the next valid LDU arrives, a new audio segment starts

The LFD NAC observations show only 33% of frames have the correct NAC (659). The remaining 67% have corrupted NAC values from simulcast multipath. This means roughly 2 out of 3 frames fail NID validation.

With P25 LDU frames arriving every ~20ms (9 IMBE frames at 20ms each = 180ms per LDU), losing 2/3 of frames means valid LDUs arrive every ~540ms on average. With 180ms holdover, there are frequent gaps > 180ms that trigger segment close/reopen.

### Problem 2: Simulcast NID Corruption
P25 NID (Network Identifier Data) contains:
- NAC (12 bits) - Network Access Code
- DUID (4 bits) - Data Unit ID
- BCH parity (48 bits for 16 data bits)

BCH(63,16,23) can correct up to 11 bit errors in the 63-bit NID. However, simulcast multipath creates correlated burst errors that can exceed BCH correction capability. When BCH fails, the entire NID is invalid and the frame is rejected.

### Problem 3: 180ms Holdover Too Short for Simulcast
The current `audioHoldoverMs=180` is adequate for single-site channels where NID failures are rare. For simulcast channels where 67% of NIDs fail, 180ms is too short — the average gap between valid LDUs is ~540ms, far exceeding the holdover period.

## Improvement Opportunities (Ordered by Impact)

### 1. Increase Audio Holdover for Simulcast (LOW RISK, HIGH IMPACT)
**Estimated improvement**: 30-50% segment reduction, 10-15% word improvement
**Effort**: Minimal code change

Increase `audioHoldoverMs` for simulcast channels. With valid LDUs arriving ~every 540ms on average:
- 600ms holdover would bridge most single-LDU gaps
- 1000ms holdover would bridge 2-LDU gaps
- The existing signal energy check (`isSignalPresent()`) prevents false holdover during actual silence

**Implementation**: Change `audioHoldoverMs` in playlist config for LFD from 180 to 600-1000. Can be tested immediately with `--force-holdover` parameter.

### 2. NAC-Biased BCH Correction (MEDIUM RISK, HIGH IMPACT)
**Estimated improvement**: 20-40% more valid LDUs
**Effort**: Moderate (BCH decoder modification)

Currently, the BCH decoder corrects the NID independently, then checks if the corrected NAC matches the configured NAC. For simulcast channels, modify the BCH decoder to:
1. Try standard BCH correction first
2. If the corrected NAC doesn't match configured NAC, try a second pass: force the NAC bits to the configured value, then check if the remaining bits (DUID + parity) are consistent
3. This leverages the known NAC to resolve ambiguous correction scenarios

This is valid because in a configured channel, we KNOW what the NAC should be. The BCH code has significant redundancy (48 parity bits for 16 data bits), and if we fix 12 of those data bits (the NAC), the remaining 4 bits (DUID) have very strong error protection.

### 3. Relaxed NAC Matching for Configured Channels (LOW RISK, MEDIUM IMPACT)
**Estimated improvement**: 5-15% more valid LDUs
**Effort**: Small code change

When a NAC is explicitly configured (non-zero), accept frames where the decoded NAC is within a Hamming distance of 2-3 from the configured NAC. This catches frames where BCH corrected most errors but left 1-3 bit errors in the NAC field.

### 4. Audio Segment Merging (NO RISK, MEDIUM IMPACT)
**Estimated improvement**: 30-50% segment reduction at output
**Effort**: Moderate (post-processing in test tool or recording pipeline)

In the decode quality test tool (or the MP3 recording module), merge consecutive audio segments that are separated by less than a configurable gap (e.g., 500ms). This doesn't fix the root cause but improves the output quality.

### 5. Enhanced Sync Pattern Detection for Simulcast (MEDIUM RISK, MEDIUM IMPACT)
**Estimated improvement**: 10-20% sync loss reduction
**Effort**: Significant (sync detector modification)

Simulcast multipath can corrupt the 48-bit P25 sync pattern. The current sync detector requires an exact match (or very close). Relaxing the sync detection threshold for simulcast channels could recover more frames.

## Testing Plan

For each improvement, test against both LFD samples:
1. Run `./gradlew runDecodeScore` with full mode and STT
2. Compare LDUs, segments, avg segment length, and word count
3. Also verify no regression on non-simulcast channels (ROC W, Derry FD, Salem Fire)

**Success criteria**:
- STT word count increase > 15%
- Average segment length > 0.5s
- No regression on non-simulcast channels (< 1% word count loss)

## Priority Order
1. Holdover increase (immediate, test-only change)
2. NAC-biased BCH correction (highest code-level impact)
3. Relaxed NAC matching (simple, complementary to #2)
4. Audio segment merging (output quality improvement)
5. Enhanced sync detection (complex, may not help much given BCH is the bottleneck)
