# Quickstart: Simulcast Audio Quality Improvements v2

## Build & Test

```bash
# Build
./gradlew build

# Run decode quality test with silence metrics
./gradlew runDecodeScore \
  -Psamples="_SAMPLES/20260124_155626_154815000_Rockingham-County_Law-Enforcement_Rock-County-PD-W_9_baseband.wav" \
  -Pplaylist=_SAMPLES/default-lsmv2.xml \
  -Poutput=/tmp/test-output \
  -Pmode=full \
  -Pjmbe=/home/kdolan/GitHub/jmbe/codec/build/libs/jmbe-1.0.9.jar \
  -PforceMod=CQPSK_V2 -PforceNac=279

# Check silence metrics in output
cat /tmp/test-output/metrics.json | python3 -m json.tool
```

## Key Files

| File | Purpose |
|------|---------|
| `CMAEqualizer.java` | Hybrid CMA/LMS/DD equalizer |
| `P25P1DecoderLSMv2.java` | Wires training signals to equalizer |
| `P25P1MessageFramer.java` | Reports sync/NID events for training |
| `DecodeQualityTest.java` | Silence detection metric in test harness |

## System Properties for Tuning

| Property | Default | Description |
|----------|---------|-------------|
| `cma.mu` | 0.001 | CMA tracking step size |
| `cma.acq.mu` | (none) | CMA acquisition step size (gear-shift) |
| `cma.trk.mu` | (none) | CMA tracking step size (gear-shift) |
| `cma.shift.ms` | (none) | Gear-shift timing in ms |
| `eq.lms.mu` | 0.05 | LMS training step size |
| `eq.dd.mu` | 0.01 | Decision-directed step size |
| `eq.dd.enable` | true | Enable decision-directed switching |

## Test Matrix

| Channel | Sample | NAC | Expected Outcome |
|---------|--------|-----|------------------|
| ROC W | 155626 | 279 | No regression: ≥227 STT words |
| LFD | Londonderry FD | 659 | Reduced silence %, improved STT |
| WPD | Mixed Samples | 1559 | Reduced silence %, improved STT |
| Derry FD | Derry FD | 2087 | Zero change (C4FM, unaffected) |
