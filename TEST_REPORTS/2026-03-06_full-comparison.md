# Decode Quality Comparison Report
**Date**: 2026-03-06
**Mode**: Full (LDU + Audio Quality + STT)

## Commits Under Test

| Role | Branch | Commit | Description |
|------|--------|--------|-------------|
| Control (pre-merge) | master | `aae1ebfe` | Before upstream merge (audio playback refactor + squeak fix) |
| Test | 014-londonderry-decoder-comparison | `51cb8d5e` | After merge + sync guard fix + Londonderry decoder work |

## Configuration
- **JMBE Codec**: jmbe-1.0.9.jar
- **STT Model**: whisper tiny (CPU)
- **Python venv**: `/home/kdolan/.claude/venvs/audio-scorer/`
- **Samples**: 17 baseband files across 7 channels, 3 modulations

## Results Summary

| Metric | Control | Test | Delta |
|--------|---------|------|-------|
| Total LDUs | 15,439 | 15,356 | -83 (-0.5%) |
| Total Valid Messages | 203,825 | 202,512 | -1,313 |
| Sync Blocked (test only) | 0 | 38,453 | N/A |

### By Modulation
| Modulation | Files | Control LDUs | Test LDUs | Delta |
|------------|-------|-------------|-----------|-------|
| C4FM | 7 | 4,884 | 4,867 | -17 (-0.3%) |
| CQPSK | 1 | 130 | 128 | -2 (-1.5%) |
| CQPSK_V2 | 9 | 10,425 | 10,361 | -64 (-0.6%) |

### STT Word Counts (Key Metric)
| Channel | File | Control | Test | Delta |
|---------|------|---------|------|-------|
| ROC W | 155626 | 266 | 217 | -18.4% |
| ROC W | 170708 | 363 | 396 | +9.1% |
| ROC W | 201848 | 227 | 294 | +29.5% |
| ROC W | 073001 | 1,463 | 1,457 | -0.4% |
| ROC W | 101458 | 1,806 | 2,050 | +13.5% |
| ROC E | 155903 | 80 | 107 | +33.8% |
| ROC E | 170714 | 151 | 181 | +19.9% |
| ROC E | 201855 | 151 | 247 | +63.6% |
| ROC E | 073006 | 1,636 | 1,551 | -5.2% |
| Derry FD | 082300 | 40 | 43 | +7.5% |
| Derry FD | 082527 | 101 | 99 | -2.0% |
| Derry FD | 082704 | 143 | 127 | -11.2% |
| Derry FD | 081100 | 120 | 139 | +15.8% |
| Derry PD | 074518 | 683 | 642 | -6.0% |
| Salem Fire | 081037 | 1,152 | 1,011 | -12.2% |
| Londonderry | 080031 | 146 | 195 | +33.6% |

**Total Words**: Control 7,378 → Test 7,324 (-0.7%)

### Distortion Events
| Channel | File | Control | Test | Delta |
|---------|------|---------|------|-------|
| ROC W | 155626 | 14 | 10 | -28.6% |
| ROC W | 170708 | 20 | 15 | -25.0% |
| ROC W | 201848 | 12 | 11 | -8.3% |
| ROC W | 073001 | 197 | 166 | -15.7% |
| ROC W | 101458 | 209 | 174 | -16.7% |
| ROC E | 155903 | 1 | 1 | 0% |
| ROC E | 170714 | 0 | 0 | 0% |
| ROC E | 201855 | 4 | 3 | -25.0% |
| ROC E | 073006 | 208 | 165 | -20.7% |
| Derry FD | 082300 | 0 | 0 | 0% |
| Derry FD | 082527 | 9 | 8 | -11.1% |
| Derry FD | 082704 | 10 | 9 | -10.0% |
| Derry FD | 081100 | 5 | 5 | 0% |
| Derry PD | 074518 | 67 | 71 | +6.0% |
| Salem Fire | 081037 | 19 | 19 | 0% |
| Londonderry | 080031 | 2 | 1 | -50.0% |

**Total Distortion**: Control 777 → Test 658 (-15.3%)

## Verdict
MIXED — Control wins on raw LDU count (-0.5%), but Test has fewer distortion events (-15.3%) and comparable word counts (-0.7%). Several individual channels show significantly more words in Test despite fewer LDUs.

## Known Issues
- Signal energy detection (RMS threshold 0.01) broken for NAC=0 files — produces absurd decode ratios (e.g., Salem Fire: 1004611%)
- Whisper tiny model has limited accuracy — results are relative, not absolute
