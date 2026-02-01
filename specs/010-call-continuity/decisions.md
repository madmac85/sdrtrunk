# Decisions Log: P25 LSM v2 Call Continuity Improvement

## 2026-02-01 - Signal-Aware Holdover Architecture

**Context**: Need to bridge the gap between sync loss and call state to prevent premature audio segment closure.

**Decision**: Implemented a multi-layer approach:
1. SyncLossMessage handling in P25P1DecoderState to trigger holdover during sync loss
2. Periodic holdover check (100ms interval) to generate CONTINUATION events independently of message receipt
3. Signal-aware squelch in P25P1AudioModule with 300ms grace period

**Rationale**:
- The existing holdover mechanism only triggered on LDU message receipt
- During sync loss, no LDU messages are generated, so holdover never triggered
- Periodic check ensures CONTINUATION events even during extended sync loss
- Audio module grace period provides additional protection at the final layer

**Alternatives Considered**:
- Extending fade timeout alone (rejected: would delay ALL call endings, not just during signal presence)
- Reducing sync loss timeout (rejected: might cause other issues with legitimate sync loss)

## 2026-02-01 - Extended Holdover Period

**Context**: Original holdover max was 250ms, which may not be sufficient during sync loss.

**Decision**: Increased MAX_AUDIO_HOLDOVER_MS to 500ms.

**Rationale**:
- Sync loss can persist for 1+ seconds during brief fades
- Extended holdover + 500ms grace = up to 1000ms of call continuity protection
- Combined with periodic check, this should significantly reduce fragmentation

**Alternatives Considered**:
- Fixed extended holdover (rejected: configurable is more flexible for different environments)

## 2026-02-01 - Daemon Thread for Periodic Check

**Context**: Periodic holdover check needs a background thread.

**Decision**: Use daemon thread with ScheduledExecutorService.

**Rationale**:
- Daemon thread ensures JVM can exit cleanly
- Single-threaded executor is lightweight
- 100ms interval balances responsiveness with overhead

**Alternatives Considered**:
- Using existing timer infrastructure (rejected: decoder state doesn't have timer access)
- Longer interval (rejected: might miss transitions)

## 2026-02-01 - Audio Module Signal Energy Provider

**Context**: Need to wire signal energy awareness to audio module.

**Decision**: Added ISignalEnergyProvider setter to P25P1AudioModule and wired in DecoderFactory.

**Rationale**:
- Maintains separation of concerns - audio module doesn't need direct decoder reference
- Interface allows different signal energy sources in future
- Null-safe implementation maintains backwards compatibility

**Alternatives Considered**:
- Direct decoder reference (rejected: creates tight coupling)
- Event-based communication (rejected: adds complexity for simple boolean check)
