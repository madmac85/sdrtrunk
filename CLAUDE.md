# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## CRITICAL: Restricted Directories

**WARNING: The following directories are OFF-LIMITS for any file modifications without explicit user authorization:**

- `/home/kdolan/SDRTrunk/` — User application data (playlists, settings, recordings)
- Any path outside `/home/kdolan/GitHub/sdrtrunk/`

Before modifying ANY file outside this repository, Claude MUST:
1. Display a clear WARNING message identifying the file path
2. Explicitly ask for authorization with the exact phrase: "Do you authorize modification of this file outside the project directory?"
3. Wait for explicit "yes" confirmation before proceeding

Violations of this policy can result in data loss (e.g., corrupted playlists, lost settings).

## Project Overview

SDRTrunk is a Java application for decoding trunked radio protocols from Software Defined Radio (SDR) sources. It supports protocols including P25 Phase 1/2, DMR, LTR, LTR-Net, MPT-1327, and auxiliary decoders (Fleetsync II, MDC-1200, etc.). Supported tuner hardware includes RTL-SDR, HackRF, AirSpy, Funcube Dongle, SDRPlay, and HydraSDR.

## Build Commands

**Prerequisites:** OpenJDK 25+ with JavaFX modules (Bellsoft Liberica JDK recommended).

```bash
./gradlew build              # Compile and run tests
./gradlew run                # Run the application
./gradlew test               # Run tests only
./gradlew clean build        # Full clean rebuild
./gradlew runtimeZipCurrent  # Create release ZIP for current OS (output in build/image/)
```

Run a single test class:
```bash
./gradlew test --tests "io.github.dsheirer.audio.DuplicateCallDetectionTest"
```

The build requires `--enable-preview` and `--add-modules=jdk.incubator.vector` (configured automatically in build.gradle) for the Project Panama Vector API.

## Architecture

### Processing Pipeline

The core signal processing follows this chain:

**Tuner Source → Channelizer → Demodulator → Decoder → Messages → Channel State**

1. **Source** (`source/tuner/`): Manages SDR hardware, provides raw I/Q sample streams
2. **Channelizer**: Extracts individual channel bandwidth from wideband tuner output
3. **Demodulator** (`module/demodulate/`): FM/AM/PSK demodulation of channelized samples
4. **Decoder** (`module/decode/`): Protocol-specific decoding (P25, DMR, etc.)
5. **Channel State** (`channel/state/`): Tracks call state, identifiers, and events

### Module System

`Module` (abstract base class) is the fundamental processing unit. Modules are composed into a `ProcessingChain` which wires together producers and consumers via listener interfaces:

- `IMessageProvider` / `IMessageListener` — decoded protocol messages
- `IRealBufferProvider` / `IRealBufferListener` — demodulated audio samples
- `IComplexSamplesListener` — raw I/Q samples
- `IAudioSegmentProvider` / `IAudioSegmentListener` — audio output segments
- `IDecodeEventProvider` / `IDecodeEventListener` — decode events (calls, data)

Inter-module communication also uses a Guava `EventBus` per processing chain, plus a global `MyEventBus` for system-wide events.

### Key Packages

| Package | Purpose |
|---------|---------|
| `module/decode/` | Protocol decoders (p25, dmr, ltr, mpt1327, passport, nbfm, am) |
| `source/tuner/` | SDR tuner drivers and management |
| `dsp/` | DSP primitives: filters, oscillators, mixers, squelch, FFT |
| `audio/` | Audio pipeline: codecs, recording, streaming, playback |
| `channel/state/` | Channel state machines (SingleChannelState, MultiChannelState) |
| `controller/channel/` | Channel lifecycle management and ChannelProcessingManager |
| `identifier/` | Classification system for radio IDs, talkgroups, frequencies |
| `gui/` | JavaFX UI (main class: `io.github.dsheirer.gui.SDRTrunk`) |
| `edac/` | Error correction: BCH, Trellis, CRC, Golay |
| `message/` | Base message classes and MessageHistory |
| `sample/` | Sample buffer types (complex, real, byte) and broadcasters |
| `preference/` | User preference management (persisted via Jackson XML) |
| `playlist/` | Channel playlist configuration |
| `record/` | Audio (WAV/MP3) and baseband binary recording |

### Protocol Decoder Structure

Each protocol decoder under `module/decode/<protocol>/` typically contains:
- `*DecoderState` — state machine tracking calls and identifiers
- `*MessageProcessor` — converts raw bits/symbols to typed messages
- `message/` — protocol-specific message class hierarchy
- `channel/` — channel grant and traffic channel handling
- `identifier/` — protocol-specific identifier types

### Configuration and Persistence

- Channel configurations and playlists are serialized as Jackson XML
- User preferences stored via the `preference/` package
- The `Channel` class represents a configured monitoring channel with source, decoder, and recording settings

### Sample/Buffer Flow

- `ComplexSamples` — interleaved I/Q float arrays with a timestamp
- `ByteBuffer` — native sample buffers from tuner hardware
- Tuner-specific buffer converters in `buffer/` (e.g., `airspy/`, `hydrasdr/`)
- `Broadcaster<T>` pattern used throughout for fan-out distribution

## Testing

JUnit 5 (Jupiter). Tests are in `src/test/java/io/github/dsheirer/`. Test coverage is minimal (6 test classes) — the project relies primarily on integration testing through the running application.

## Source Layout

```
src/main/java/io/github/dsheirer/   # All application source (~2600 files)
src/test/java/io/github/dsheirer/   # Test source
src/main/resources/                  # Images, CSS, udev rules, logback.xml
```

The `sourceSets` in build.gradle map `src/main` and `src/test` directly (not `src/main/java`), so the Java source root is `src/main/java/`.

## Active Technologies
- Java 25+ (OpenJDK with `--enable-preview`) + JMBE 1.0.0 (IMBE codec), java-lame (MP3 encoding) (013-fix-c4fm-audio-corruption)
- N/A (file-based test I/O only) (013-fix-c4fm-audio-corruption)

## Recent Changes
- 013-fix-c4fm-audio-corruption: Added Java 25+ (OpenJDK with `--enable-preview`) + JMBE 1.0.0 (IMBE codec), java-lame (MP3 encoding)
