# Implementation Tasks: P25 LSM v2 Call Continuity Improvement

## Task 1: Add SyncLossMessage Handling to P25P1DecoderState

**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderState.java`

**Description**: Handle SyncLossMessage to trigger holdover check during sync loss events.

**Steps**:
1. Add import for `SyncLossMessage`
2. Add case in `receive()` switch statement for `SYNC_LOSS` message type
3. Implement `processSyncLoss()` method:
   - Check if signal energy provider indicates active transmission
   - If in active call (mLastValidLDUTimestamp > 0), broadcast CONTINUATION event
   - Use extended holdover period for sync loss (holdoverMs + 500ms)

**Acceptance Criteria**:
- SyncLossMessage triggers holdover check
- CONTINUATION event is broadcast when signal is present during sync loss
- No CONTINUATION events when signal is absent

---

## Task 2: Add Signal Energy Provider to P25P1AudioModule

**File**: `src/main/java/io/github/dsheirer/module/decode/p25/audio/P25P1AudioModule.java`

**Description**: Add signal energy awareness to the audio module for smarter squelch handling.

**Steps**:
1. Add `ISignalEnergyProvider` field and setter method
2. Add grace period constant: `AUDIO_GRACE_PERIOD_MS = 300`
3. Add `mGracePeriodEndTime` field to track grace period state
4. Modify `SquelchStateListener.receive()`:
   - On SQUELCH, check if signal is still present
   - If signal present and not in grace period, start grace period and return
   - If signal present and in grace period but not expired, return
   - If signal absent or grace period expired, close audio segment

**Acceptance Criteria**:
- Audio segment closure is delayed when signal is present
- Grace period expires and closes segment if signal remains absent
- No impact when signal energy provider is null

---

## Task 3: Wire Signal Energy Provider to Audio Module in DecoderFactory

**File**: `src/main/java/io/github/dsheirer/module/decode/DecoderFactory.java`

**Description**: Connect the LSM v2 decoder's signal energy provider to the P25P1AudioModule.

**Steps**:
1. Store reference to P25P1AudioModule when created
2. After existing wiring for decoder state (line ~326), add wiring for audio module:
   ```java
   if(lsmv2Decoder != null && audioModule != null)
   {
       audioModule.setSignalEnergyProvider(lsmv2Decoder);
   }
   ```
3. Ensure audioModule is accessible in scope

**Acceptance Criteria**:
- Audio module receives signal energy provider for LSM v2 channels
- No changes for non-LSM v2 channels
- No null pointer exceptions

---

## Task 4: Increase Maximum Audio Holdover Configuration

**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/DecodeConfigP25Phase1.java`

**Description**: Increase the maximum holdover period to allow longer decode error tolerance.

**Steps**:
1. Change `MAX_AUDIO_HOLDOVER_MS` from 250 to 500
2. Update javadoc comment if present

**Acceptance Criteria**:
- Configuration accepts holdover values up to 500ms
- Existing configurations with values ≤250ms continue to work

---

## Task 5: Add Periodic Holdover Check to P25P1DecoderState

**File**: `src/main/java/io/github/dsheirer/module/decode/p25/phase1/P25P1DecoderState.java`

**Description**: Add timer-based periodic holdover check independent of message receipt.

**Steps**:
1. Add `ScheduledExecutorService mHoldoverExecutor` field
2. Add `ScheduledFuture<?> mHoldoverTask` field
3. Implement `startHoldoverCheck()` method:
   - Create executor if null
   - Schedule task every 100ms
4. Implement `stopHoldoverCheck()` method:
   - Cancel task
   - Shutdown executor
5. Implement `periodicHoldoverCheck()` method:
   - Check signal energy provider for active signal
   - If signal present and within extended holdover period, broadcast CONTINUATION
6. Call `startHoldoverCheck()` when entering CALL state (in processLDU when valid)
7. Call `stopHoldoverCheck()` when call ends (TDU/TDULC processing)
8. Add cleanup in `stop()` method

**Acceptance Criteria**:
- Periodic check runs during active calls
- CONTINUATION events keep call alive during signal presence
- Executor is properly cleaned up on stop

---

## Task 6: Integration Testing with Sample Files

**Description**: Test changes against existing sample recordings to verify call continuity improvement.

**Steps**:
1. Use existing LSMv2ComparisonTest infrastructure
2. Create test that counts audio segments per transmission
3. Run against 8 sample files
4. Compare before/after segment counts
5. Verify no regression in total decoded audio duration

**Acceptance Criteria**:
- SC-001: Audio segment fragmentation reduced by 80%
- SC-002: Call events per transmission ≤ LSM decoder
- SC-004: 95% of single transmissions produce one audio segment
- SC-003: No decrease in total decoded audio duration

---

## Task 7: Reset Grace Period State on UNSQUELCH

**File**: `src/main/java/io/github/dsheirer/module/decode/p25/audio/P25P1AudioModule.java`

**Description**: Reset the grace period tracking when a new call starts.

**Steps**:
1. In SquelchStateListener, add handling for UNSQUELCH state
2. Reset `mGracePeriodEndTime = 0` on UNSQUELCH

**Acceptance Criteria**:
- Grace period state doesn't persist across calls
- Each call starts with fresh grace period tracking

---

## Task Dependencies

```
Task 1 ─┐
        ├──→ Task 5 ──→ Task 6
Task 4 ─┘

Task 2 ──→ Task 3 ──→ Task 7 ──→ Task 6
```

Tasks 1, 2, and 4 can be done in parallel.
Task 3 depends on Task 2.
Task 5 depends on Task 1.
Task 7 depends on Task 2.
Task 6 (testing) depends on all other tasks.
