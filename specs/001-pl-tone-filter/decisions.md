# Implementation Decisions

## 2026-01-24 - Goertzel Algorithm for Tone Detection
**Context**: Need to detect a single CTCSS frequency in demodulated audio.
**Decision**: Use the Goertzel algorithm (already available in codebase as `GoertzelFilter`) as the basis for tone detection.
**Rationale**: Goertzel is optimal for detecting a single frequency — it's essentially a single-bin DFT and far more efficient than a full FFT when only one frequency matters.
**Alternatives Considered**: Full FFT (wasteful for single frequency), IIR bandpass filter + envelope detector (more complex, less precise).

## 2026-01-24 - Dual-Gate Squelch Architecture
**Context**: Need to combine noise squelch with PL tone detection.
**Decision**: Keep noise squelch operating independently. Add CTCSS detection as a second gate that further filters the noise squelch output.
**Rationale**: Preserves all existing behavior. Noise squelch still provides the primary signal/no-signal detection. CTCSS adds a layer on top. Both must pass for audio to flow.
**Alternatives Considered**: Replace noise squelch entirely with CTCSS-gated squelch (loses noise squelch benefits), combine into single squelch (more complex, harder to maintain).

## 2026-01-24 - Detection Before High-Pass Filter
**Context**: CTCSS tones are 67-250 Hz. The noise squelch uses a 3 kHz high-pass filter internally.
**Decision**: Feed demodulated audio to CTCSSDetector before it enters the noise squelch processing.
**Rationale**: The noise squelch's high-pass filter removes sub-audible content, which would eliminate the CTCSS tone. Detection must occur on unfiltered demodulated audio.

## 2026-01-24 - Custom Goertzel Implementation
**Context**: Existing `GoertzelFilter` class uses `int` sample rate and `long` target frequency, which doesn't suit CTCSS (fractional Hz frequencies like 114.8).
**Decision**: Implement tone detection directly in `CTCSSDetector` using the Goertzel algorithm with `double` precision for frequency and sample rate.
**Rationale**: The existing GoertzelFilter has API limitations (integer-only frequencies) that don't accommodate CTCSS fractional frequencies. A focused implementation within CTCSSDetector is cleaner than modifying the existing utility class.

## 2026-01-24 - Tone Removal via Existing Audio Filter
**Context**: FR-008 requires removing the sub-audible PL tone from decoded audio output.
**Decision**: Rely on the existing "High-Pass Audio Filter" toggle (already in the UI and audio module) which filters below ~300 Hz.
**Rationale**: The audio module already has a configurable high-pass filter that removes sub-audible content. No new filter needed. When audio filter is enabled (default), CTCSS tones are already removed from the output.

## 2026-01-24 - Package Location
**Context**: Where to place the new CTCSS classes.
**Decision**: Place `CTCSSFrequency` and `CTCSSDetector` in `io.github.dsheirer.dsp.squelch` package alongside `NoiseSquelch`.
**Rationale**: CTCSS detection is a form of squelch control (tone squelch). It logically belongs with the other squelch implementations.
