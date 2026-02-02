# Decisions Log: P25 LSM v2 Comprehensive Decode Optimization

## 2026-02-02 - Voice-Only Channel Configuration Options

**Context**: The spec requires support for dedicated voice channels that never have encryption or control channel traffic. Decode errors can produce garbage data that falsely triggers encrypted call or control channel detection.

**Decision**: Implemented two configuration flags in DecodeConfigP25Phase1:
1. `ignoreEncryptionState` - When true, assumes all calls are unencrypted and begins audio processing immediately on LDU1 without waiting for encryption state verification
2. `ignoreControlChannelState` - When true, never transitions to CONTROL state, preventing false detections from garbage data

**Rationale**:
- These are opt-in flags with safe defaults (false)
- Allows dedicated voice channels to avoid the specific failure modes described in the spec
- Zero risk for normal operation since disabled by default
- Implementation is minimal and low-risk

**Alternatives Considered**:
- Automatic detection of voice-only channels based on observed traffic patterns (rejected: too complex, could have false positives)
- Global configuration for all channels (rejected: would break trunking scenarios)

## 2026-02-02 - Audio Frame Concealment Strategy

**Context**: The spec requires minimizing audible artifacts in decoded audio. Corrupted IMBE frames passed to JMBE produce harsh digital noise/artifacts.

**Decision**: Implemented energy-based frame validation with configurable concealment:
1. Frame validation based on energy spikes (8x threshold) and sudden drops
2. Three concealment strategies: NONE, REPEAT_LAST (default), SILENCE
3. Concealment tracks frame statistics for diagnostic purposes

**Rationale**:
- Energy-based detection is simple and catches most corruption artifacts (loud noise, sudden silence)
- REPEAT_LAST as default produces natural-sounding concealment
- Configurability allows users to tune for their preference
- Frame statistics enable measurement of concealment effectiveness

**Alternatives Considered**:
- IMBE bit-level validation using Hamming codes (rejected: requires deep IMBE frame parsing, high complexity)
- JMBE codec modification to report frame quality (rejected: external library, not under our control)
- Spectral analysis for artifact detection (rejected: computationally expensive, overkill)

## 2026-02-02 - Control Channel State Bypass Implementation

**Context**: Need to prevent false control channel state transitions without breaking legitimate control channel operation.

**Decision**: Created helper method `broadcastControlState()` that checks the ignore flag and broadcasts either CONTROL or IDLE state. All CONTROL state transitions go through this method.

**Rationale**:
- Single point of control for all CONTROL state broadcasts
- Easy to maintain and verify complete coverage
- IDLE state is a safe fallback that keeps the decoder running without side effects
- No architectural changes required

**Alternatives Considered**:
- Filtering at the state machine level (rejected: would require changes to SingleChannelState)
- Message filtering to drop control channel messages (rejected: would lose diagnostic information)
