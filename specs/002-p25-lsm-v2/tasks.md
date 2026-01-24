# Implementation Tasks

## Task 1: Add CQPSK_V2 to Modulation enum
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/Modulation.java`
**Action**: Add `CQPSK_V2("Conventional (LSM v2)")` after `CQPSK`
**Dependencies**: None

## Task 2: Create P25P1DemodulatorLSMv2
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DemodulatorLSMv2.java`
**Action**: Create new demodulator class based on P25P1DemodulatorLSM with:
- Transmission boundary detection (sample energy monitoring, 200ms silence threshold)
- Cold-start reset: zero previousSymbol state, skip first differential output
- Adaptive PLL gain: 0.25 acquisition → 0.05 tracking (transition at 50 symbols)
- Fast AGC: 0.2 factor for first 20 symbols, then 0.05
- Gardner TED suppression for first 2 symbols after reset
**Dependencies**: Task 1

## Task 3: Create P25P1DecoderLSMv2
**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderLSMv2.java`
**Action**: Create decoder class matching P25P1DecoderLSM structure but using P25P1DemodulatorLSMv2
**Dependencies**: Task 2

## Task 4: Update DecoderFactory
**File**: `src/main/java/io/github/dsheirer/module/decode/DecoderFactory.java`
**Action**: Add `case CQPSK_V2: modules.add(new P25P1DecoderLSMv2()); break;`
**Dependencies**: Task 3

## Task 5: Update P25P1ConfigurationEditor
**File**: `src/main/java/io/github/dsheirer/gui/playlist/channel/P25P1ConfigurationEditor.java`
**Action**:
- Add `mLSMv2ToggleButton` field and getter
- Add to segmented button: `getButtons().addAll(getC4FMToggleButton(), getLSMToggleButton(), getLSMv2ToggleButton())`
- Update `setDecoderConfiguration()` to handle 3-way modulation
- Update `saveDecoderConfiguration()` to detect which button is selected
- Update help label text
**Dependencies**: Task 1
