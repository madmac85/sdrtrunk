# Tasks: PL Tone (CTCSS) Filter for Analog FM Channels

## Task 1: Create CTCSSFrequency Enum
**Status**: Complete
**File**: `src/main/java/io/github/dsheirer/dsp/squelch/CTCSSFrequency.java`

Enum defining all 38 standard EIA/TIA CTCSS tone frequencies plus NONE. Each entry has a frequency value (double Hz) and display label.

## Task 2: Create CTCSSDetector DSP Component
**Status**: Complete
**File**: `src/main/java/io/github/dsheirer/dsp/squelch/CTCSSDetector.java`
**Depends on**: Task 1

Goertzel-based tone detector that processes demodulated audio buffers. Features:
- Detects a specific CTCSS frequency with +/- 2% tolerance
- Uses hysteresis (4 windows open, 6 windows close) on 50ms detection blocks
- Three Goertzel bins (center, low, high) for tolerance coverage

## Task 3: Add CTCSS Tone Field to DecodeConfigNBFM
**Status**: Complete
**File**: `src/main/java/io/github/dsheirer/module/decode/nbfm/DecodeConfigNBFM.java`
**Depends on**: Task 1

Added `CTCSSFrequency mCTCSSFrequency` field with Jackson XML annotation (`ctcssFrequency` attribute). Defaults to NONE for backward compatibility.

## Task 4: Integrate CTCSSDetector into NBFMDecoder
**Status**: Complete
**File**: `src/main/java/io/github/dsheirer/module/decode/nbfm/NBFMDecoder.java`
**Depends on**: Tasks 2, 3

Modified NBFMDecoder to:
- Instantiate CTCSSDetector when config has non-NONE CTCSS frequency
- Feed demodulated audio to detector before noise squelch
- Gate noise squelch audio output: only pass when CTCSS tone detected (or detector is null)
- Configure detector sample rate when channel sample rate changes

## Task 5: Add PL Tone Selector to NBFMConfigurationEditor
**Status**: Complete
**File**: `src/main/java/io/github/dsheirer/gui/playlist/channel/NBFMConfigurationEditor.java`
**Depends on**: Task 3

Added ComboBox<CTCSSFrequency> to the decoder pane with:
- All 38 CTCSS tones plus "None" option
- Tooltip explaining PL tone functionality
- Wired into setDecoderConfiguration/saveDecoderConfiguration
- Disabled when no NBFM config loaded

## Task 6: Write Unit Tests for CTCSSDetector
**Status**: Complete
**File**: `src/test/java/io/github/dsheirer/dsp/squelch/CTCSSDetectorTest.java`
**Depends on**: Task 2

JUnit 5 tests covering:
- Detection of target frequency (114.8 Hz)
- Rejection of different CTCSS frequency (127.3 Hz)
- Detection within +/- 1.5% tolerance
- No false positive from white noise
- Hysteresis prevents instant detection
- Tone detection and loss lifecycle
- Detection with voice audio present
- Rejection of adjacent CTCSS tone
