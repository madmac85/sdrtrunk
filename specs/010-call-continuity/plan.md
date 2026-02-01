# Implementation Plan: P25 LSM v2 Call Continuity Improvement

## Root Cause Analysis

### Problem Flow

When using the LSM v2 decoder, calls are being fragmented into multiple audio segments. Investigation reveals a 5-layer chain where any layer can trigger premature call ending:

```
Signal → Framer → DecoderState → StateMachine → AudioModule
                                     ↓
                        SquelchController → SQUELCH event
```

### Key Findings

1. **Holdover mechanism exists but has limited coverage**
   - Signal energy provider IS wired to decoder state (DecoderFactory:320-326)
   - Default holdover is 180ms (DecodeConfigP25Phase1)
   - `checkAndApplyHoldover()` generates CONTINUATION events during decode errors

2. **Critical gap: Holdover only triggers on LDU message receipt**
   - When sync is lost, the framer stops producing LDU messages
   - After 1 second, framer broadcasts `SyncLossMessage` (P25P1MessageFramer:330-333)
   - `SyncLossMessage` is NOT handled by `P25P1DecoderState` - no holdover check occurs
   - Without LDU messages, `checkAndApplyHoldover()` is never called

3. **Fade timeout forces call end regardless of signal**
   - `StateMachine.checkState()` transitions CALL → FADE after 1200ms without decoder events
   - `StateMonitoringSquelchController.stateChanged()` broadcasts SQUELCH for any state != CALL
   - `P25P1AudioModule` closes audio segment on any SQUELCH event

4. **Audio module doesn't consider RF signal state**
   - Closes audio segment unconditionally on SQUELCH
   - No awareness of whether transmission is still active

### Sequence Leading to Call Fragmentation

```
T+0ms:    Last valid LDU decoded, CONTINUATION event sent
T+180ms:  Holdover period expires (no new valid LDUs)
T+200ms:  Invalid LDU received - checkAndApplyHoldover() called but signal check fails
T+500ms:  Sync loss due to decode errors
T+1000ms: SyncLossMessage broadcast (NOT handled by decoder state)
T+1200ms: Fade timeout expires → CALL → FADE → SQUELCH → closeAudioSegment()
```

But RF signal energy may still indicate an active transmission!

---

## Implementation Strategy

### Approach: Signal-Aware Holdover Bridge

Create a mechanism that bridges the gap between sync loss and holdover, using RF signal energy to keep calls alive during extended decode errors.

### Key Changes

1. **P25P1DecoderState: Handle SyncLossMessage for holdover**
   - Add SyncLossMessage handling to trigger holdover check
   - When sync is lost but signal is present, broadcast CONTINUATION events

2. **P25P1DecoderState: Periodic holdover check**
   - Add timer-based holdover check independent of message receipt
   - Check signal energy every 100ms during call state
   - Broadcast CONTINUATION if signal present

3. **P25P1AudioModule: Signal-aware squelch handling**
   - Add ISignalEnergyProvider reference
   - Delay audio segment closure when signal is present
   - Use configurable grace period before forcing close

4. **Configuration: Extended holdover options**
   - Increase max holdover from 250ms to 500ms
   - Add audio grace period configuration (default 300ms)

---

## Detailed Design

### Change 1: SyncLossMessage Handling in P25P1DecoderState

**File**: `P25P1DecoderState.java`

Add handler for SyncLossMessage:

```java
case SYNC_LOSS:
    processSyncLoss(message);
    break;
```

```java
private void processSyncLoss(IMessage message)
{
    // During sync loss, check if signal indicates active transmission
    if(mSignalEnergyProvider != null && mSignalEnergyProvider.isSignalPresent())
    {
        // Broadcast continuation to keep call alive during sync loss
        // but only if we're in an active call state
        if(mLastValidLDUTimestamp > 0)
        {
            long timeSinceLastLDU = message.getTimestamp() - mLastValidLDUTimestamp;
            if(timeSinceLastLDU < mHoldoverMs + 500) // Extended grace for sync loss
            {
                broadcast(new DecoderStateEvent(this, Event.CONTINUATION, State.CALL));
            }
        }
    }
}
```

### Change 2: Periodic Holdover Check via Scheduled Task

**File**: `P25P1DecoderState.java`

Add a periodic check that runs independent of message receipt:

```java
private ScheduledExecutorService mHoldoverExecutor;
private ScheduledFuture<?> mHoldoverTask;

private void startHoldoverCheck()
{
    if(mHoldoverExecutor == null)
    {
        mHoldoverExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    if(mHoldoverTask == null || mHoldoverTask.isDone())
    {
        mHoldoverTask = mHoldoverExecutor.scheduleAtFixedRate(
            this::periodicHoldoverCheck, 100, 100, TimeUnit.MILLISECONDS);
    }
}

private void periodicHoldoverCheck()
{
    if(mSignalEnergyProvider != null && mSignalEnergyProvider.isSignalPresent() &&
       mLastValidLDUTimestamp > 0)
    {
        long timeSinceLastLDU = System.currentTimeMillis() - mLastValidLDUTimestamp;
        if(timeSinceLastLDU < mHoldoverMs + 500)
        {
            broadcast(new DecoderStateEvent(this, Event.CONTINUATION, State.CALL));
        }
    }
}
```

### Change 3: Signal-Aware Audio Module

**File**: `P25P1AudioModule.java`

Add signal energy awareness to squelch handling:

```java
private ISignalEnergyProvider mSignalEnergyProvider;
private long mGracePeriodEndTime = 0;
private static final long AUDIO_GRACE_PERIOD_MS = 300;

public void setSignalEnergyProvider(ISignalEnergyProvider provider)
{
    mSignalEnergyProvider = provider;
}

// In SquelchStateListener.receive():
if(event.getSquelchState() == SquelchState.SQUELCH)
{
    // If signal is still present, delay closing
    if(mSignalEnergyProvider != null && mSignalEnergyProvider.isSignalPresent())
    {
        if(mGracePeriodEndTime == 0)
        {
            mGracePeriodEndTime = System.currentTimeMillis() + AUDIO_GRACE_PERIOD_MS;
            return; // Don't close yet
        }
        else if(System.currentTimeMillis() < mGracePeriodEndTime)
        {
            return; // Still in grace period
        }
    }

    // Signal not present or grace period expired
    closeAudioSegment();
    mGracePeriodEndTime = 0;
    mEncryptedCallStateEstablished = false;
    mEncryptedCall = false;
    mCachedLDUMessages.clear();
}
```

### Change 4: Wire Signal Energy Provider to Audio Module

**File**: `DecoderFactory.java`

```java
// After creating audio module
P25P1AudioModule audioModule = new P25P1AudioModule(userPreferences, aliasList);
if(lsmv2Decoder != null)
{
    audioModule.setSignalEnergyProvider(lsmv2Decoder);
}
modules.add(audioModule);
```

### Change 5: Configuration Updates

**File**: `DecodeConfigP25Phase1.java`

```java
public static final int MAX_AUDIO_HOLDOVER_MS = 500; // Increased from 250
```

---

## Implementation Order

1. **P25P1DecoderState**: Add SyncLossMessage handling
2. **P25P1AudioModule**: Add signal energy provider and grace period
3. **DecoderFactory**: Wire signal energy provider to audio module
4. **DecodeConfigP25Phase1**: Increase max holdover to 500ms
5. **P25P1DecoderState**: Add periodic holdover check (if needed after testing)

---

## Testing Strategy

### Unit Tests
- Verify SyncLossMessage triggers holdover check
- Verify audio module delays close when signal present
- Verify grace period expiration closes segment

### Integration Tests
- Process sample files with known transmissions
- Compare audio segment count before/after changes
- Verify no increase in missed audio duration

### Acceptance Criteria
- SC-001: Audio segment fragmentation reduced by 80%
- SC-002: Call events per transmission ≤ LSM decoder
- SC-004: 95% of single transmissions produce one audio segment

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Over-extended calls merge separate transmissions | Use signal energy drop detection, require silence gap |
| Periodic task impacts performance | Single executor, 100ms interval is lightweight |
| Grace period causes audio artifacts | Configurable, default conservative 300ms |

---

## Files to Modify

| File | Changes |
|------|---------|
| `P25P1DecoderState.java` | Add SyncLossMessage handling, periodic holdover check |
| `P25P1AudioModule.java` | Add signal energy provider, grace period logic |
| `DecoderFactory.java` | Wire signal energy provider to audio module |
| `DecodeConfigP25Phase1.java` | Increase MAX_AUDIO_HOLDOVER_MS to 500 |
