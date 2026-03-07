# Londonderry FD Decode Baseline
**Date**: 2026-03-06
**Branch**: master (commit e00220c7+)
**Decoder**: CQPSK_V2 (LSM v2)
**Frequency**: 156.1125 MHz
**Modulation**: CQPSK/Simulcast

## Channel Characteristics
- **Town**: Londonderry, NH
- **FCC License**: KQO260
- **Type**: Conventional repeater, simulcast
- **NAC**: 659 decimal (0x293 hex)
  - Playlist was configured with NAC=293 (hex entered as decimal - WRONG)
  - Corrected to 659 gives +3.1% LDU improvement
- **Simulcast**: Yes - hundreds of unique NAC values observed from multipath NID corruption
  - Dominant NAC 659 appears ~1,166 out of ~3,500 observations (33%)
  - Next most common: 915(x31), 643(x30), 658(x26), 667(x26) - all 1-2 bit errors from 659

## Baseline Metrics

### Old Sample (Feb 18, 2026) - 6,669s recording
| Metric | NAC=293 (wrong) | NAC=659 (correct) | Delta |
|--------|-----------------|-------------------|-------|
| LDUs | 552 | 569 | +17 (+3.1%) |
| Valid Messages | 2,701 | 2,676 | -25 |
| Sync Blocked | 3,205 | 3,287 | +82 |
| Audio Seconds | 99.4s | 102.4s | +3.0s |
| Audio Segments | 339 | 345 | +6 |
| Avg Segment Length | 0.29s | 0.30s | +0.01s |
| Signal Seconds | 101.4s | 101.4s | 0 |
| Decode Ratio | 98.0% | 101.0% | +3.0pp |
| STT Words | 797 | **874** | **+77 (+9.7%)** |
| Bit Errors | 17,105 | 16,780 | -325 |
| Sync Losses | 7,958 | 7,946 | -12 |

### New Sample (Mar 6, 2026) - 8,058s recording
| Metric | NAC=293 (wrong) | NAC=659 (correct) | Delta |
|--------|-----------------|-------------------|-------|
| LDUs | 455 | 503 | +48 (+10.6%) |
| Valid Messages | 1,867 | 2,120 | +253 |
| Sync Blocked | 3,210 | 3,350 | +140 |
| Audio Seconds | 81.9s | 90.5s | +8.6s |
| Audio Segments | 404 | 377 | -27 (better!) |
| Avg Segment Length | 0.20s | 0.24s | +0.04s |
| Signal Seconds | 61.3s | 61.3s | 0 |
| Decode Ratio | 133.6% | 147.7% | +14.1pp |
| STT Words | 733 | **814** | **+81 (+11.1%)** |
| Bit Errors | 15,649 | 16,976 | +1,327 |
| Sync Losses | 9,591 | 9,582 | -9 |

### Summary with Correct NAC (659)
| Metric | Old (Feb 18) | New (Mar 6) |
|--------|-------------|-------------|
| Recording Length | 6,669s (1h51m) | 8,058s (2h14m) |
| LDUs | 569 | 503 |
| Audio Seconds | 102.4s | 90.5s |
| Audio Segments | 345 | 377 |
| Avg Segment Length | 0.30s | 0.24s |
| STT Words | 874 | 814 |
| Words per LDU | 1.54 | 1.62 |
| Decode Ratio | 101.0% | 147.7% |

## Key Problems

### 1. Extreme Audio Fragmentation
Average segment length is only **0.24-0.30 seconds**. This means audio is being broken into extremely short segments, making it hard to understand speech. For comparison:
- ROC W averages 1.2-1.5s segments
- Salem Fire averages 0.8-1.0s segments
- LFD is 3-5x worse than other channels

### 2. Simulcast NID Corruption
The simulcast environment causes massive NAC scatter. The dominant NAC (659) only appears 33% of the time. The decoder sees hundreds of spurious NAC values from multipath-corrupted NID fields. This likely causes:
- Missed LDUs (NAC filter rejects valid frames with corrupted NIDs)
- Sync losses from NID decode failures
- Audio breaks between transmission segments

### 3. High Bit Error Rate
~17,000 bit errors for 569 LDUs = ~30 errors/LDU. This is much higher than non-simulcast channels (ROC W: ~3 errors/LDU).

### 4. Poor Decode Ratio on New Sample
The Mar 6 sample shows 147.7% decode ratio (decoding more audio than signal detected), suggesting either:
- Signal detection threshold is still not calibrated well for this channel
- Simulcast multipath causes intermittent signal below threshold while still decodable

## Improvement Opportunities

1. **Fix NAC configuration** (DONE - corrected to 659) - +9.7-11.1% word improvement
2. **NAC validation tolerance for simulcast** - Accept frames where NAC is within BCH correction distance of configured NAC, rather than exact match
3. **Reduce audio segmentation** - Investigate why audio holdover isn't bridging short gaps. Current audioHoldoverMs=180 may be too short for simulcast
4. **Simulcast-aware sync** - NID BCH correction should prioritize correcting to configured NAC before rejecting
5. **Increase error tolerance** - Consider accepting LDUs with higher bit error counts in simulcast environments

## Test Commands
```bash
# Old sample (Feb 18)
./gradlew runDecodeScore -Psamples="_SAMPLES/Londonderry FD" -Pplaylist="_SAMPLES/default-lsmv2.xml" -Poutput=/tmp/lfd-test -Pmode=full -PforceMod=CQPSK_V2 -PforceNac=659 -Pjmbe=/home/kdolan/GitHub/jmbe/codec/build/libs/jmbe-1.0.9.jar

# New sample (Mar 6)
./gradlew runDecodeScore -Psamples="_SAMPLES/Mixed Samples 3-6-2026/20260306_074453_156112500_Municipalities_Londonderry_Londonderry-FD_14_baseband.wav" -Pplaylist="_SAMPLES/default-lsmv2.xml" -Poutput=/tmp/lfd-test -Pmode=full -PforceMod=CQPSK_V2 -PforceNac=659 -Pjmbe=/home/kdolan/GitHub/jmbe/codec/build/libs/jmbe-1.0.9.jar
```
