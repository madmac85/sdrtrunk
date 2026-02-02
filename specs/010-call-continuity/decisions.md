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

## 2026-02-02 - Removed Audio Module Grace Period (Bug Fix)

**Context**: Audio segments were not closing properly. The "Now Playing" tab showed call ended, but audio notification still showed channel playing and recordings were not sent to RDIO until application exit.

**Decision**: Removed the signal-aware grace period from P25P1AudioModule entirely.

**Rationale**:
- The grace period logic assumed SQUELCH events would arrive periodically to check expiration
- In reality, SQUELCH is a one-time state change event (StateMonitoringSquelchController only fires when state changes)
- When grace period started and we returned without closing, no follow-up events arrived
- The audio segment stayed open indefinitely, preventing recordings from being sent to streaming services
- The decoder state's holdover mechanism (SyncLossMessage handling + periodic CONTINUATION events) already prevents premature SQUELCH by keeping the state machine in CALL state
- The audio module grace period was redundant and caused this critical bug

**Bug Symptoms**:
- Call ended in "Now Playing" tab
- Audio notification area still showed channel playing
- Recording never sent to RDIO during session
- Recording only sent when application exited (cleanup)

**Fix**: Reverted P25P1AudioModule.SquelchStateListener to original simple form that immediately closes audio segment on SQUELCH. The holdover protection now works at the decoder state layer only, which is the correct architectural placement.
