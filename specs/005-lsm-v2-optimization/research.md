# P25 Conventional PTT Channel Research

## Sources

- [Tait Radio Academy - P25 Channel Operation](https://www.taitradioacademy.com/topic/p25-channel-operation-1/)
- [DVS P25 Training Guide](https://www.dvsinc.com/papers/p25_training_guide.pdf)
- [Project 25 Wikipedia](https://en.wikipedia.org/wiki/Project_25)
- TIA-102.BAAA - P25 Common Air Interface

## P25 Phase 1 Overview

Project 25 (P25) is a suite of standards for digital mobile radio communications. Phase 1 uses FDMA with a 12.5 kHz channel bandwidth and either C4FM (Continuous 4-level FM) or CQPSK (LSM - Linear Simulcast Modulation) for the modulation scheme.

### Modulation Details

| Parameter | Value |
|-----------|-------|
| Symbol Rate | 4800 baud |
| Bits per Symbol | 2 (QPSK) |
| Bit Rate | 9600 bps |
| Voice Data | 4400 bps (IMBE) |
| FEC | 2800 bps |
| Signaling | 2400 bps |

## Voice Transmission Frame Structure

### Transmission Sequence

```
[Carrier On] → [HDU] → [LDU1] → [LDU2] → [LDU1] → [LDU2] → ... → [TDU/TDULC] → [Carrier Off]
```

### Header Data Unit (HDU)

- **Purpose**: Identifies call destination, encryption status, talkgroup
- **Length**: 648 bits (81 bytes)
- **Duration**: ~135ms at 4800 baud
- **Contents**:
  - Message Indicator (MI)
  - Manufacturer ID (MFID)
  - Algorithm ID (ALGID)
  - Key ID (KID)
  - Talkgroup ID (TGID)
  - Golay(24,12,8) FEC

### Logical Data Unit 1 (LDU1)

- **Purpose**: Voice frame + Link Control
- **Length**: 1568 bits (196 bytes)
- **Duration**: ~327ms at 4800 baud
- **Contents**:
  - 9 IMBE voice codewords (20ms each = 180ms audio)
  - Link Control (LC) - source/destination IDs
  - Low-speed data
  - Hamming(10,6,3) and Reed-Solomon FEC

### Logical Data Unit 2 (LDU2)

- **Purpose**: Voice frame + Encryption Sync
- **Length**: 1568 bits (196 bytes)
- **Duration**: ~327ms at 4800 baud
- **Contents**:
  - 9 IMBE voice codewords (20ms each = 180ms audio)
  - Encryption Sync (ES) - ALGID, KID, MI
  - Low-speed data
  - Hamming(10,6,3) and Reed-Solomon FEC

### Superframe Structure

LDU1 and LDU2 alternate to form superframes:
- **Superframe Duration**: 360ms (LDU1 + LDU2)
- **Voice per Superframe**: 18 IMBE codewords (360ms audio)
- **Signaling per Superframe**: LC in LDU1, ES in LDU2

### Terminator Data Unit (TDU)

- **Purpose**: Signal end of transmission (PTT release)
- **Length**: 28 bits (3.5 bytes)
- **Duration**: ~5.8ms at 4800 baud
- **Contents**: Minimal - just sync + NID

### Terminator Data Unit with Link Control (TDULC)

- **Purpose**: Signal end of transmission + final LC update
- **Length**: Longer than TDU, includes LC block
- **Contents**: Sync + NID + Link Control (source/dest confirmation)

## Network ID (NID)

Every data unit begins with a sync pattern followed by NID:

### Sync Pattern
- **Length**: 48 bits (24 dibits)
- **Pattern**: 0x5575F5FF77FF
- **Only contains**: D01_PLUS_3 and D11_MINUS_3 dibits
- **Detection**: Correlation-based (soft) or bit-error (hard, up to 4 errors)

### NID Structure
- **Length**: 64 bits (32 dibits)
- **NAC Field**: Bits 0-11 (12-bit Network Access Code)
- **DUID Field**: Bits 12-15 (4-bit Data Unit ID)
- **BCH Parity**: Bits 16-63 (BCH(63,16,23) protection)
- **Error Correction**: Up to 11 bit errors correctable

### Data Unit IDs (DUID)

| DUID | Value | Description |
|------|-------|-------------|
| HDU | 0x0 | Header Data Unit |
| TDU | 0x3 | Terminator Data Unit |
| LDU1 | 0x5 | Logical Data Unit 1 |
| TSDU | 0x7 | Trunking Signaling Data Unit |
| LDU2 | 0xA | Logical Data Unit 2 |
| PDU | 0xC | Packet Data Unit |
| TDULC | 0xF | Terminator with Link Control |

## Conventional vs Trunked Operation

### Conventional (PTT) Channels

- Carrier turns **on** when PTT pressed
- Carrier turns **off** when PTT released
- No control channel coordination
- Each transmission is independent
- Same NAC typically used for all transmissions on channel

### Trunked Channels

- Continuous carrier on traffic channels
- Control channel coordinates channel assignments
- Multiple simultaneous calls on different frequencies
- Dynamic channel allocation

## Cold-Start Acquisition Challenges

### Problem Statement

On conventional PTT channels, the decoder must acquire sync "cold" at the start of each transmission. This differs from trunked channels where the carrier is continuous and the decoder maintains lock.

### Timing Budget

From carrier-on to first decodable symbol:

| Phase | Duration | Notes |
|-------|----------|-------|
| Carrier rise | 1-5ms | Transmitter PA ramp-up |
| AGC settling | 2-4ms | ~20 symbols at 0.05 gain factor |
| PLL acquisition | 5ms | 24 symbols at higher gain |
| Timing acquisition | 0.8ms | 4 symbols suppression |
| Sync detection | 5ms | Full sync pattern |
| NID decode | 6.7ms | 32 dibits + BCH |
| **Total** | ~20-26ms | Before first message content |

### HDU Window

- HDU begins immediately after sync/NID at transmission start
- HDU duration: ~135ms
- If cold-start acquisition takes 26ms, we've already consumed ~20% of HDU

### Signal Strength Variations

- Transmitter output may vary during PTT press
- Signal path may change (mobile in motion)
- Interference from adjacent channels
- Multipath in urban environments

## Error Correction Mechanisms

### BCH(63,16,23)

Used for NID error correction:
- 16 information bits (NAC + DUID)
- 47 parity bits
- Can correct up to 11 bit errors
- Galois Field GF(2^6)

### NAC-Assisted BCH Decode

When NAC is known/tracked:
1. First attempt: Standard BCH decode
2. If fails and tracked NAC available:
   - Insert tracked NAC into message
   - Re-run BCH decode
   - May correct additional errors

### Golay(24,12,8)

Used in HDU for critical fields:
- 12 information bits
- 12 parity bits
- Can correct up to 3 bit errors

### Reed-Solomon

Used in LDU for voice and link control:
- RS(24,12,13) for LC words
- Provides burst error correction

### Hamming(10,6,3)

Used for IMBE voice frame protection:
- 6 information bits
- 4 parity bits
- Single error correction

## Optimization Opportunities

### 1. Adaptive Sync Threshold

The sync detector uses correlation. Lower correlation scores may still indicate valid sync when:
- Signal is weak but present
- Partial symbol errors due to timing
- Adjacent channel interference

**Opportunity**: Use energy-aware adaptive threshold - lower threshold when signal energy is high but standard sync fails.

### 2. Boundary Recovery Window

When transmission boundary is detected:
- We know a new transmission is starting
- HDU should arrive within ~20ms
- More aggressive sync search is warranted

**Opportunity**: Enable parallel hard/soft sync detection for first 100ms after boundary.

### 3. TDU Prediction

TDU is very short (28 bits) and arrives during signal fade:
- Energy derivative indicates impending transmission end
- Previous DUID context (LDU2 typically precedes TDU)
- Partial sync patterns may indicate TDU

**Opportunity**: When energy fades rapidly and previous frame was LDU, lower sync threshold and add TDU-specific recovery.

### 4. NAC Pre-Seeding

On conventional channels, NAC is typically constant:
- Same transmitter site
- Same channel configuration
- NAC rarely changes

**Opportunity**: Persist tracked NAC across transmissions (already implemented in v2 baseline).

## References to Codebase

### Signal Processing Chain
- `P25P1DecoderLSMv2.java:163` - Transmission boundary detection
- `P25P1DecoderLSMv2.java:183-224` - Energy-based boundary algorithm
- `P25P1DemodulatorLSMv2.java:117-133` - Cold-start reset

### Sync Detection
- `P25P1MessageFramer.java:52` - SYNC_DETECTION_THRESHOLD = 60
- `P25P1SoftSyncDetector.java` - Soft correlation sync
- `P25P1HardSyncDetector.java:29` - MAXIMUM_BIT_ERROR = 4

### NID Processing
- `P25P1MessageFramer.java:890-928` - NID check and BCH decode
- `BCH_63_16_23_P25.java:55-68` - NAC-assisted BCH decode
- `NACTracker.java:116-139` - NAC tracking algorithm

### Message Assembly
- `P25P1MessageAssembler.java:105-160` - Fuzzy DUID estimation
- `P25P1MessageFramer.java:201-213` - Message assembler creation
