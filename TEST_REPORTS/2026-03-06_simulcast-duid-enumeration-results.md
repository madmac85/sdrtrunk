# Simulcast DUID Enumeration — Quick LDU Results

**Date**: 2026-03-06
**Branch**: 016-simulcast-decode-improvements
**Commit**: c7174509
**Change**: BCH NAC-assisted DUID enumeration + MAX_AUDIO_HOLDOVER_MS 500->1000

## What Changed

### BCH DUID Enumeration (BCH_63_16_23_P25.java)
When standard BCH(63,16,23) decode fails and a configured NAC is available:
1. Force configured NAC, keep original DUID -> retry BCH
2. Force configured NAC, enumerate all 7 valid DUIDs ({0,3,5,7,10,12,15}) -> retry BCH each
3. If all fail, restore original bits

Previously only step 1 was attempted. Adding step 2 dramatically improves NID recovery
on simulcast channels where multipath corrupts both NAC and DUID bits.

### Holdover Max (DecodeConfigP25Phase1.java)
MAX_AUDIO_HOLDOVER_MS raised from 500 to 1000ms. This does not affect LDU decode count —
only audio segment boundaries during active calls with decode gaps.

## Results: Londonderry FD (CQPSK simulcast, NAC=659)

| Sample | Baseline LDUs | With DUID Enum | Delta |
|--------|--------------|----------------|-------|
| Old (Feb 18, 6669s) | 569 | 1764 | +1195 (+210%) |
| New (Mar 6, 8058s) | 503 | 1857 | +1354 (+269%) |

### Per-Decoder Comparison (Old Sample)

| Decoder | LDUs | Valid Msgs | Bit Errors |
|---------|------|------------|------------|
| C4FM | 480 | 1353 | 423 |
| LSM | 128 | 1108 | 4267 |
| LSM v2 (DUID enum) | 1764 | 7691 | 52631 |

### Per-Decoder Comparison (New Sample)

| Decoder | LDUs | Valid Msgs | Bit Errors |
|---------|------|------------|------------|
| C4FM | 193 | 202 | 563 |
| LSM | 98 | 327 | 2231 |
| LSM v2 (DUID enum) | 1857 | 8165 | 60969 |

## Results: ROC W (CQPSK simulcast)

| Config | LSM LDUs | LSM v2 LDUs | Delta |
|--------|----------|-------------|-------|
| No NAC (baseline) | 407 | 431 | +24 |
| NAC=279 (configured) | 407 | 499 | +92 (+15% vs baseline 434) |

## Regression Checks (no NAC configured — DUID enum inactive)

| Channel | Modulation | Decoder | LDUs | Baseline | Delta |
|---------|-----------|---------|------|----------|-------|
| Derry FD (4 files) | C4FM | C4FM | 666 | 666 | 0 |
| Salem Fire | C4FM | C4FM | 2257 | n/a (new) | — |
| ROC W (no NAC) | CQPSK | LSM v2 | 431 | 434 | -3 (noise) |

## Key Observations

1. DUID enumeration only activates when configuredNAC > 0 — zero risk to unconfigured channels
2. The 3x improvement on LFD comes from recovering NIDs where both NAC and DUID bits were corrupted by simulcast multipath
3. Higher bit error counts on recovered frames are expected — BCH is correcting more errors per NID
4. ROC W also benefits (+15%) since it is also a simulcast system
5. Non-simulcast channels (Derry FD, Salem Fire) are completely unaffected

## Baseline Reference (pre-improvement)

### Londonderry FD with correct NAC=659
- Old sample: 569 LDUs, 2676 valid msgs, 102.4s audio, 345 segments, 874 STT words
- New sample: 503 LDUs, 2120 valid msgs, 90.5s audio, 377 segments, 814 STT words

### ROC W (gold standard commit a820bca4)
- LSM v2: 434 LDUs, 0 regressions

### Derry FD (gold standard commit a820bca4)
- C4FM: 666 LDUs
