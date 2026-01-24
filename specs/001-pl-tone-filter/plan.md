# Implementation Plan: PL Tone (CTCSS) Filter for Analog FM Channels

## Architecture Overview

The PL tone filter integrates into the existing NBFM decoder signal processing chain as a secondary squelch gate. The detection happens on demodulated audio (where the sub-audible tone is present) before the existing noise squelch processes the audio.

### Processing Chain (Modified)

```
ComplexSamples → Decimation → FM Demod → [CTCSSDetector] → NoiseSquelch → Resampler → Audio Out
                                              ↕
                                    (gates NoiseSquelch output)
```

The CTCSSDetector operates in parallel with the noise squelch — it monitors the same demodulated audio stream for the configured tone. The noise squelch still handles the primary audio gating (noise-based), but its output is additionally gated by the CTCSS detector when a tone is configured.

## Key Design Decisions

1. **Goertzel-based detection**: Use the Goertzel algorithm (single-frequency DFT) for efficient tone detection. This is ideal for detecting a single known frequency.

2. **Detection operates on pre-squelch audio**: The CTCSS tone is sub-audible (67-250 Hz) and would be removed by the noise squelch's 3 kHz high-pass filter. Detection must happen before that filter.

3. **Dual-gate model**: NoiseSquelch handles noise gating as before. CTCSSDetector provides an additional gate. Audio passes only when both gates are open. When no CTCSS is configured, the CTCSS gate is always open (transparent).

4. **Hysteresis**: The detector uses a configurable number of consecutive detection windows to confirm tone presence/absence, avoiding chattering.

5. **Tone removal from audio**: The existing "High-Pass Audio Filter" option in the audio module already removes sub-audible frequencies. No additional filtering needed.

## Components

### 1. CTCSSFrequency Enum (`dsp/squelch/CTCSSFrequency.java`)
- NONE + 38 standard frequencies
- Frequency value (double Hz)
- Display label

### 2. CTCSSDetector (`dsp/squelch/CTCSSDetector.java`)
- Goertzel-based tone detector
- Processes demodulated audio buffers
- Maintains tone-detected state with hysteresis
- Sample-rate aware (configured via setSampleRate)

### 3. DecodeConfigNBFM (modified)
- New field: `CTCSSFrequency mCTCSSFrequency = CTCSSFrequency.NONE`
- Jackson XML serialization
- Backward compatible (null/missing defaults to NONE)

### 4. NBFMDecoder (modified)
- Conditional CTCSSDetector instantiation
- Feed demodulated audio to detector
- Gate audio listener output based on detector state

### 5. NBFMConfigurationEditor (modified)
- ComboBox for CTCSS tone selection
- Wired into save/load configuration flow
