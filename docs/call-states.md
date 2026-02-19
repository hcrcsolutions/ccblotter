# Call States and Event Lifecycle

This document describes the call event model used by the Admin UI system. It covers
the Kafka event types, their SIP triggers, the resulting Redis state changes, and
the expected behavior for every call scenario.

## Event Types

| Event Type | Description | SIP Trigger | Kafka Partition Key |
|---|---|---|---|
| `CALL_QUEUED` | Call entered ACD queue | 182 Queued sent to caller | originator |
| `CALL_ROUTED_TO_AGENT` | Agent answered the call | 200 OK from agent UA | agentId |
| `CALL_ENDED` | Call terminated | BYE from either party | agentId |
| `CALL_ABANDONED` | Caller hung up while in queue | CANCEL from caller | originator |
| `CALL_HOLD_CHANGED` | Hold/resume toggled | re-INVITE with SDP change | agentId |

### Naming note

`CALL_ROUTED_TO_AGENT` fires on the agent's **200 OK** (answer), not on 180 Ringing.
The system does not track the ringing phase. From the Admin UI's perspective, a call
comes into existence the moment the agent picks up.

## Agent States

| State | Meaning |
|---|---|
| `ONLINE` | Logged in, available to receive calls |
| `ON_CALL` | Actively handling a call |
| `AWAY` | On break (lunch, coffee, training, etc.) |
| `UNAVAILABLE` | Logged out |

## Scenarios

### 1. Inbound call routed through queue (answered)

The standard ACD flow. A customer calls in, the call is queued by skill and priority,
and the ACD routes it to an available agent who answers.

```
Customer              SIP Server / ACD              Agent
   |                        |                         |
   |---- INVITE ----------->|                         |
   |<--- 182 Queued --------|  --> CALL_QUEUED         |
   |                        |                         |
   |     ... waiting in queue ...                     |
   |                        |                         |
   |                        |---- INVITE ------------>|
   |                        |<--- 180 Ringing --------|  (no event)
   |                        |<--- 200 OK -------------|  --> CALL_ROUTED_TO_AGENT
   |<--- 200 OK ------------|                         |
   |---- ACK -------------->|---- ACK --------------->|
   |                        |                         |
   |     === media flows ============================  |
   |                        |                         |
   |---- BYE -------------->|---- BYE --------------->|  --> CALL_ENDED
   |<--- 200 OK ------------|<--- 200 OK -------------|
```

**Events produced (in order):**

1. `CALL_QUEUED` (partition: originator)
   - Payload: callId, originator, skill, priority, queuedAtMs, sipServerId
   - Redis: creates `queue:call:{id}` hash, adds to `queue:calls` set

2. `CALL_ROUTED_TO_AGENT` (partition: agentId)
   - Payload: callId (new B-leg ID), agentId, agentName, originator, queuedCallId, skill, callStartTimeMs, sipServerId
   - Redis: removes `queue:call:{queuedCallId}` from queue, creates `call:{id}` hash, adds to `calls:active` set, sets agent to `ON_CALL`
   - `queuedCallId` links this event to the original `CALL_QUEUED` event

3. `CALL_ENDED` (partition: agentId)
   - Payload: callId, agentId, originator, callStartTimeMs, callEndTimeMs, durationSeconds, reason, sipServerId
   - Redis: writes last-call metadata to agent hash, removes `call:{id}`, sets agent to `ONLINE`

**Admin UI shows:**
- Call appears in queue panel when CALL_QUEUED arrives
- Call moves from queue to active calls panel when CALL_ROUTED_TO_AGENT arrives; agent shows as ON_CALL
- Call disappears from active panel when CALL_ENDED arrives; agent returns to ONLINE

### 2. Inbound call abandoned in queue

The customer hangs up before the ACD routes the call to an agent.

```
Customer              SIP Server / ACD
   |                        |
   |---- INVITE ----------->|
   |<--- 182 Queued --------|  --> CALL_QUEUED
   |                        |
   |     ... waiting ...    |
   |                        |
   |---- CANCEL ----------->|  --> CALL_ABANDONED
   |<--- 200 OK ------------|
```

**Events produced:**

1. `CALL_QUEUED` (partition: originator)
2. `CALL_ABANDONED` (partition: originator)
   - Payload: callId, originator, queuedAtMs, abandonedAtMs, waitDurationSeconds, sipServerId
   - Redis: removes `queue:call:{id}` from queue (idempotent)

No agent is involved. No agent state changes.

**Admin UI shows:**
- Call appears in queue panel, then disappears when abandoned.

### 3. Direct call: customer calls agent (answered)

The customer dials an agent's extension directly, bypassing the ACD queue.
No CALL_QUEUED event is produced.

```
Customer              SIP Server                    Agent
   |                        |                         |
   |---- INVITE ----------->|                         |
   |                        |---- INVITE ------------>|
   |                        |<--- 180 Ringing --------|  (no event)
   |<--- 180 Ringing -------|                         |
   |                        |<--- 200 OK -------------|  --> CALL_ROUTED_TO_AGENT
   |<--- 200 OK ------------|                         |
   |---- ACK -------------->|---- ACK --------------->|
   |                        |                         |
   |     === media flows ============================  |
   |                        |                         |
   |---- BYE -------------->|---- BYE --------------->|  --> CALL_ENDED
```

**Events produced:**

1. `CALL_ROUTED_TO_AGENT` (partition: agentId)
   - `queuedCallId` = **null** (signals a direct call; queue removal is skipped)
   - `skill` = **null** (not skill-routed)
   - All other fields same as the queued scenario

2. `CALL_ENDED` (partition: agentId)
   - Identical to the queued scenario

**Admin UI shows:**
- No queue entry. Call appears directly in the active calls panel. Agent shows as ON_CALL.

### 4. Direct call: customer calls agent (caller hangs up before answer)

The customer dials an agent directly but hangs up while the agent's phone is ringing.

```
Customer              SIP Server                    Agent
   |                        |                         |
   |---- INVITE ----------->|                         |
   |                        |---- INVITE ------------>|
   |                        |<--- 180 Ringing --------|  (no event)
   |<--- 180 Ringing -------|                         |
   |---- CANCEL ----------->|---- CANCEL ------------>|  (no event)
   |<--- 200 OK ------------|<--- 200 OK -------------|
```

**Events produced:** None.

`CALL_ROUTED_TO_AGENT` fires on 200 OK, which never arrived. No Redis state was
created. No cleanup is needed.

`CALL_ABANDONED` does not apply here — it is specifically for calls that were
queued (had a prior `CALL_QUEUED` event).

**Admin UI shows:** Nothing. This call was invisible to the system.

### 5. Agent-to-agent direct call (answered)

Agent A dials Agent B directly. Both agents transition to ON_CALL. Two events are
needed because each agent's state is managed independently on separate Kafka
partitions.

```
Agent A               SIP Server                    Agent B
   |                        |                         |
   |---- INVITE ----------->|                         |
   |                        |---- INVITE ------------>|
   |                        |<--- 200 OK -------------|  --> 2x CALL_ROUTED_TO_AGENT
   |<--- 200 OK ------------|                         |     (one per agent)
   |---- ACK -------------->|---- ACK --------------->|
   |                        |                         |
   |     === media flows ============================  |
   |                        |                         |
   |---- BYE -------------->|---- BYE --------------->|  --> 2x CALL_ENDED
```

**Events produced:**

1. `CALL_ROUTED_TO_AGENT` for Agent A (partition: Agent A's ID)
   - agentId = Agent A, originator = Agent B's ID
   - queuedCallId = null, skill = null

2. `CALL_ROUTED_TO_AGENT` for Agent B (partition: Agent B's ID)
   - agentId = Agent B, originator = Agent A's ID
   - queuedCallId = null, skill = null

3. `CALL_ENDED` for Agent A (partition: Agent A's ID)

4. `CALL_ENDED` for Agent B (partition: Agent B's ID)

Both event pairs use the **same callId**. This means the `call:{id}` hash in Redis
is written twice — the second write overwrites the first. The active call record
will show only one agent (last writer wins). Both agents independently transition
ON_CALL -> ONLINE.

**Admin UI shows:**
- One call record in the active calls panel (showing one agent leg)
- Both agents show as ON_CALL
- Both agents return to ONLINE when the call ends

**Known limitation:** The single callId means the Admin UI cannot display both
legs of the call as a linked pair. If this is needed in the future, use distinct
call IDs per leg (e.g., `{callId}-a` / `{callId}-b`) and add a `linkedCallId`
field to correlate them.

### 6. Agent-to-agent direct call (callee declines or no answer)

Agent A dials Agent B, but Agent B rejects the call or doesn't pick up.

```
Agent A               SIP Server                    Agent B
   |                        |                         |
   |---- INVITE ----------->|                         |
   |                        |---- INVITE ------------>|
   |                        |<--- 180 Ringing --------|  (no event)
   |                        |<--- 486 Busy Here ------|  (no event)
   |<--- 486 Busy Here -----|                         |
   |---- ACK -------------->|                         |
```

**Events produced:** None.

No 200 OK was received, so `CALL_ROUTED_TO_AGENT` was never fired. No Redis state
exists. Neither agent's state changed.

**Admin UI shows:** Nothing.

### 7. Hold and resume during an active call

Applies to any active call (queued or direct). The agent puts the caller on hold
and later resumes.

```
Agent                 SIP Server                    Caller
   |                        |                         |
   |  (call is active, state = TALKING)               |
   |                        |                         |
   |---- re-INVITE -------->|  (SDP: a=sendonly)      |
   |                        |---- re-INVITE --------->|  --> CALL_HOLD_CHANGED
   |                        |<--- 200 OK -------------|     (newState = ON_HOLD)
   |<--- 200 OK ------------|                         |
   |                        |                         |
   |  (call is on hold)     |                         |
   |                        |                         |
   |---- re-INVITE -------->|  (SDP: a=sendrecv)      |
   |                        |---- re-INVITE --------->|  --> CALL_HOLD_CHANGED
   |                        |<--- 200 OK -------------|     (newState = TALKING)
   |<--- 200 OK ------------|                         |
```

**Events produced:**

1. `CALL_HOLD_CHANGED` (partition: agentId)
   - Payload: callId, agentId, newState (`"ON_HOLD"` or `"TALKING"`), sipServerId
   - Redis: updates `state` field in `call:{id}` hash (atomic Lua: only if call exists)

The agent remains `ON_CALL` throughout. Only the call's state toggles.

**Admin UI shows:**
- Call record changes between TALKING and ON_HOLD indicators.

### 8. Call collision (new call while agent is already ON_CALL)

The ACD routes a new call to an agent who is already handling a call. This can
happen due to race conditions between SIP servers or stale ACD state.

The receiver handles this automatically in `SipEventHandler.handleCallRoutedToAgent()`:

1. Detects that the agent is already `ON_CALL` with an existing `currentCallId`
2. Force-removes the existing call record from Redis
3. Creates the new call record and updates the agent's `currentCallId`
4. When the stale `CALL_ENDED` arrives for the old call, it is ignored because
   the agent's `currentCallId` no longer matches

No special event type is needed. The collision is resolved by the receiver.

## Partition Key Rules

Events are partitioned to guarantee ordering per entity:

| Partition Key | Used By | Guarantee |
|---|---|---|
| agentId | All agent state events, CALL_ROUTED_TO_AGENT, CALL_ENDED, CALL_HOLD_CHANGED | All events for one agent are processed in order |
| originator | CALL_QUEUED, CALL_ABANDONED | All events for one caller are processed in order |

`CALL_QUEUED` (originator partition) is linked to `CALL_ROUTED_TO_AGENT` (agentId
partition) via the `queuedCallId` field. The `removeFromQueue` operation is
idempotent, making this cross-partition link safe.

## Redis Key Schema (Call-Related)

| Key Pattern | Type | Contents |
|---|---|---|
| `call:{id}` | Hash | id, originator, agentId, agentName, startTime, state, skill |
| `calls:active` | Set | All active call IDs |
| `queue:call:{id}` | Hash | id, originator, skill, priority, queuedAt |
| `queue:calls` | Set | All queued call IDs |
| `agent:{id}` | Hash | id, name, state, stateChangedAt, currentCallId, lastCall*, ... |
| `agents:by-state:{state}` | Set | Agent IDs in each state (ONLINE, ON_CALL, AWAY, UNAVAILABLE) |

## Scenarios Not Covered by the Current Model

The following scenarios are invisible to the Admin UI because no events are fired:

- **Ringing phase**: The system does not track when an agent's phone starts
  ringing (180 Ringing). The call only becomes visible when answered (200 OK).
  Adding ringing visibility would require a new agent state (`RINGING`), a
  ringing event, and decline/timeout events for rollback.

- **Direct call not answered**: When a caller hangs up before the agent answers
  a direct call, no Redis state was created and no cleanup is needed.

- **IVR / pre-queue processing**: Any call processing that happens before the
  ACD queues the call (IVR menus, authentication, skill determination) is
  handled internally by the SIP platform and is not surfaced as events.
