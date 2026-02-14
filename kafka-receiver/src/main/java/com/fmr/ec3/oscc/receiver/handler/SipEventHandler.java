package com.fmr.ec3.oscc.receiver.handler;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.sip.*;
import com.fmr.ec3.oscc.receiver.state.AgentStateWriter;
import com.fmr.ec3.oscc.receiver.state.CallStateWriter;
import com.fmr.ec3.oscc.receiver.state.QueueStateWriter;
import com.fmr.ec3.oscc.receiver.websocket.WebSocketBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Handles SIP call-control events from SIP servers.
 * Topic: ccblotter.sip.events (partitioned by agentId or originator).
 *
 * <h3>kafka-sender integration: SIP message → event mapping</h3>
 *
 * <pre>
 * ┌─────────────────────────────────────┬──────────────────────┬───────────────┐
 * │ SIP Message / Trigger              │ Event Type           │ Partition Key │
 * ├─────────────────────────────────────┼──────────────────────┼───────────────┤
 * │ REGISTER (Expires > 0)             │ AGENT_LOGGED_IN      │ agentId       │
 * │ REGISTER (Expires: 0) or timeout   │ AGENT_LOGGED_OUT     │ agentId       │
 * │ SUBSCRIBE/NOTIFY presence → DND    │ AGENT_BREAK_STARTED  │ agentId       │
 * │ SUBSCRIBE/NOTIFY presence → avail  │ AGENT_BREAK_ENDED    │ agentId       │
 * │ INVITE → ACD queues (182 Queued)   │ CALL_QUEUED          │ originator    │
 * │ INVITE → agent 200 OK (answered)   │ CALL_ROUTED_TO_AGENT │ agentId       │
 * │ BYE (either party)                 │ CALL_ENDED           │ agentId       │
 * │ CANCEL from caller while queued    │ CALL_ABANDONED       │ originator    │
 * │ re-INVITE hold SDP (a=sendonly)    │ CALL_HOLD_CHANGED    │ agentId       │
 * │ re-INVITE resume SDP (a=sendrecv)  │ CALL_HOLD_CHANGED    │ agentId       │
 * └─────────────────────────────────────┴──────────────────────┴───────────────┘
 * </pre>
 *
 * <h4>Partition key rules</h4>
 * <ul>
 *   <li>Agent events always use agentId — ensures all state for one agent is serialized.</li>
 *   <li>CALL_QUEUED and CALL_ABANDONED use originator — no agent is assigned yet.
 *       This is the only cross-partition interaction: CALL_QUEUED (originator key) is later
 *       linked to CALL_ROUTED_TO_AGENT (agentId key) via queuedCallId. The removeFromQueue
 *       operation is idempotent so this is safe.</li>
 * </ul>
 *
 * <h4>Payload field mapping from SIP headers</h4>
 * <ul>
 *   <li>AGENT_LOGGED_IN: agentId from REGISTER AOR (e.g. sip:AGT-0001@sip.example.com),
 *       agentName from display name or directory lookup, skills from ACD configuration.</li>
 *   <li>AGENT_LOGGED_OUT: reason from de-registration cause — "explicit_logout" for Expires:0,
 *       "registration_expired" for timeout, "end_of_shift" for scheduled.</li>
 *   <li>AGENT_BREAK_STARTED: breakType from presence note or custom header
 *       ("Lunch", "Coffee", "Personal", "Training").</li>
 *   <li>CALL_QUEUED: callId from Call-ID header, originator from From header user part,
 *       skill from DNIS/dialed-number mapping, priority from caller profile or DNIS rule,
 *       queuedAtMs = timestamp when 182 Queued was sent.</li>
 *   <li>CALL_ROUTED_TO_AGENT: callId = new Call-ID for the B-leg (agent leg),
 *       queuedCallId = original Call-ID from CALL_QUEUED (links the two events),
 *       callStartTimeMs = timestamp of agent's 200 OK.</li>
 *   <li>CALL_ENDED: callStartTimeMs/callEndTimeMs from dialog timestamps,
 *       reason from SIP Reason header (e.g. "normal_clearing", "user_busy").</li>
 *   <li>CALL_ABANDONED: callId must match the callId from CALL_QUEUED,
 *       abandonedAtMs = timestamp of CANCEL or TCP disconnect.</li>
 *   <li>CALL_HOLD_CHANGED: newState = "ON_HOLD" when SDP has a=sendonly/a=inactive,
 *       "TALKING" when SDP has a=sendrecv. Triggered by re-INVITE from agent UA.</li>
 * </ul>
 *
 * All payloads include sipServerId = the nodeId of the SIP server producing the event.
 */
@Component
public class SipEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SipEventHandler.class);

    private final AgentStateWriter agentWriter;
    private final CallStateWriter callWriter;
    private final QueueStateWriter queueWriter;
    private final WebSocketBroadcaster broadcaster;

    public SipEventHandler(AgentStateWriter agentWriter, CallStateWriter callWriter,
                            QueueStateWriter queueWriter, WebSocketBroadcaster broadcaster) {
        this.agentWriter = agentWriter;
        this.callWriter = callWriter;
        this.queueWriter = queueWriter;
        this.broadcaster = broadcaster;
    }

    public void handle(EventEnvelope<?> envelope) {
        switch (envelope.eventType()) {
            case EventType.AGENT_LOGGED_IN -> handleAgentLoggedIn(envelope);
            case EventType.AGENT_LOGGED_OUT -> handleAgentLoggedOut(envelope);
            case EventType.AGENT_BREAK_STARTED -> handleAgentBreakStarted(envelope);
            case EventType.AGENT_BREAK_ENDED -> handleAgentBreakEnded(envelope);
            case EventType.CALL_QUEUED -> handleCallQueued(envelope);
            case EventType.CALL_ROUTED_TO_AGENT -> handleCallRoutedToAgent(envelope);
            case EventType.CALL_ENDED -> handleCallEnded(envelope);
            case EventType.CALL_ABANDONED -> handleCallAbandoned(envelope);
            case EventType.CALL_HOLD_CHANGED -> handleCallHoldChanged(envelope);
            default -> log.warn("Unknown SIP event type: {}", envelope.eventType());
        }
    }

    private void handleAgentLoggedIn(EventEnvelope<?> envelope) {
        AgentLoggedInPayload p = (AgentLoggedInPayload) envelope.payload();

        // Check if agent has an active call that needs cleanup (e.g., re-registration while on call)
        List<String> snapshot = agentWriter.getAgentStateAndCallId(p.agentId());
        String currentState = snapshot.get(0);
        String existingCallId = snapshot.get(1);

        if ("ON_CALL".equals(currentState) && existingCallId != null) {
            log.warn("Agent {} re-registered while ON_CALL with call {} - removing orphaned call",
                p.agentId(), existingCallId);
            callWriter.removeCall(existingCallId);
        }

        agentWriter.saveAgent(p.agentId(), p.agentName(), "ONLINE", null);
        broadcaster.broadcastAgents();
        broadcaster.broadcastCalls();
        broadcaster.broadcastSummary();
    }

    private void handleAgentLoggedOut(EventEnvelope<?> envelope) {
        AgentLoggedOutPayload p = (AgentLoggedOutPayload) envelope.payload();

        // Check if agent has an active call that needs cleanup
        List<String> snapshot = agentWriter.getAgentStateAndCallId(p.agentId());
        String currentState = snapshot.get(0);
        String existingCallId = snapshot.get(1);

        if ("ON_CALL".equals(currentState) && existingCallId != null) {
            log.warn("Agent {} logged out while ON_CALL with call {} - removing orphaned call",
                p.agentId(), existingCallId);
            callWriter.removeCall(existingCallId);
        }

        if (currentState != null) {
            agentWriter.updateAgentState(p.agentId(), "UNAVAILABLE", null);
        } else {
            log.warn("AGENT_LOGGED_OUT for unknown agent {}", p.agentId());
            agentWriter.saveAgent(p.agentId(), "Unknown", "UNAVAILABLE", null);
        }
        broadcaster.broadcastAgents();
        broadcaster.broadcastCalls();
        broadcaster.broadcastSummary();
    }

    private void handleAgentBreakStarted(EventEnvelope<?> envelope) {
        AgentBreakStartedPayload p = (AgentBreakStartedPayload) envelope.payload();

        // Check if agent has an active call that needs cleanup
        List<String> snapshot = agentWriter.getAgentStateAndCallId(p.agentId());
        String currentState = snapshot.get(0);
        String existingCallId = snapshot.get(1);

        if ("ON_CALL".equals(currentState) && existingCallId != null) {
            log.warn("Agent {} went on break while ON_CALL with call {} - removing orphaned call",
                p.agentId(), existingCallId);
            callWriter.removeCall(existingCallId);
        }

        if (currentState != null) {
            agentWriter.updateAgentState(p.agentId(), "AWAY", null);
        } else {
            log.warn("AGENT_BREAK_STARTED for unknown agent {}", p.agentId());
            agentWriter.saveAgent(p.agentId(), "Unknown", "AWAY", null);
        }
        broadcaster.broadcastAgents();
        broadcaster.broadcastCalls();
        broadcaster.broadcastSummary();
    }

    private void handleAgentBreakEnded(EventEnvelope<?> envelope) {
        AgentBreakEndedPayload p = (AgentBreakEndedPayload) envelope.payload();

        // Check if agent has an active call that needs cleanup
        List<String> snapshot = agentWriter.getAgentStateAndCallId(p.agentId());
        String currentState = snapshot.get(0);
        String existingCallId = snapshot.get(1);

        if ("ON_CALL".equals(currentState) && existingCallId != null) {
            log.warn("Agent {} break ended while ON_CALL with call {} - removing orphaned call",
                p.agentId(), existingCallId);
            callWriter.removeCall(existingCallId);
        }

        if (currentState != null) {
            agentWriter.updateAgentState(p.agentId(), "ONLINE", null);
        } else {
            log.warn("AGENT_BREAK_ENDED for unknown agent {}", p.agentId());
            agentWriter.saveAgent(p.agentId(), "Unknown", "ONLINE", null);
        }
        broadcaster.broadcastAgents();
        broadcaster.broadcastCalls();
        broadcaster.broadcastSummary();
    }

    private void handleCallQueued(EventEnvelope<?> envelope) {
        CallQueuedPayload p = (CallQueuedPayload) envelope.payload();
        queueWriter.addToQueue(p.callId(), p.originator(), p.skill(),
            p.priority(), Instant.ofEpochMilli(p.queuedAtMs()));
        broadcaster.broadcastQueue();
    }

    private void handleCallRoutedToAgent(EventEnvelope<?> envelope) {
        CallRoutedToAgentPayload p = (CallRoutedToAgentPayload) envelope.payload();

        // 1. Single HMGET: check existence + get state + get currentCallId (1 RT instead of 3)
        List<String> snapshot = agentWriter.getAgentStateAndCallId(p.agentId());
        String currentState = snapshot.get(0);
        String existingCallId = snapshot.get(1);

        // 2. Ensure agent exists (create-on-reference if missing)
        if (currentState == null) {
            log.warn("CALL_ROUTED_TO_AGENT for unknown agent {} - creating on reference", p.agentId());
            agentWriter.saveAgent(p.agentId(), p.agentName(), "ONLINE", null);
        }

        // 3. If agent is already ON_CALL, force-end their current call
        if ("ON_CALL".equals(currentState) && existingCallId != null) {
            log.warn("Agent {} already ON_CALL with call {} - force-ending", p.agentId(), existingCallId);
            callWriter.removeCall(existingCallId);
        }

        // 4. Remove queued call — idempotent, no exists check needed
        if (p.queuedCallId() != null) {
            queueWriter.removeFromQueue(p.queuedCallId());
        }

        // 5. Create Call record with event's callId
        Instant startTime = Instant.ofEpochMilli(p.callStartTimeMs());
        callWriter.saveCall(p.callId(), p.originator(), p.agentId(),
            p.agentName(), startTime, "TALKING");

        // 6. Update agent to ON_CALL
        agentWriter.updateAgentState(p.agentId(), "ON_CALL", p.callId());

        // 7. Broadcast
        broadcaster.broadcastAgents();
        broadcaster.broadcastCalls();
        broadcaster.broadcastQueue();
        broadcaster.broadcastSummary();
    }

    private void handleCallEnded(EventEnvelope<?> envelope) {
        CallEndedPayload p = (CallEndedPayload) envelope.payload();

        // 1. Check that this CALL_ENDED matches the agent's current call.
        //    A stale CALL_ENDED (e.g., after a force-end from CALL_ROUTED_TO_AGENT)
        //    must NOT clobber the agent's state for their new active call.
        List<String> snapshot = agentWriter.getAgentStateAndCallId(p.agentId());
        String currentState = snapshot.get(0);
        String currentCallId = snapshot.get(1);

        if (currentState != null && p.callId().equals(currentCallId)) {
            // Write last-call metadata first, while agent is still ON_CALL.
            // This avoids a race window where the agent is ONLINE with missing metadata.
            Instant startTime = Instant.ofEpochMilli(p.callStartTimeMs());
            Instant endTime = Instant.ofEpochMilli(p.callEndTimeMs());
            agentWriter.updateLastCallInfo(p.agentId(), p.originator(),
                startTime, endTime, p.durationSeconds());
            agentWriter.updateAgentState(p.agentId(), "ONLINE", null);
        } else if (currentState != null) {
            // Agent exists but is on a different call — stale CALL_ENDED, skip agent update
            log.warn("CALL_ENDED for call {} but agent {} has currentCallId={} - skipping agent state change",
                p.callId(), p.agentId(), currentCallId);
        } else {
            log.warn("CALL_ENDED for unknown agent {} - skipping agent update", p.agentId());
        }

        // 2. Remove call from Redis — always, idempotent
        callWriter.removeCall(p.callId());

        // 3. Broadcast
        broadcaster.broadcastAgents();
        broadcaster.broadcastCalls();
        broadcaster.broadcastSummary();
    }

    private void handleCallAbandoned(EventEnvelope<?> envelope) {
        CallAbandonedPayload p = (CallAbandonedPayload) envelope.payload();

        // removeFromQueue is idempotent — no exists check needed
        queueWriter.removeFromQueue(p.callId());

        broadcaster.broadcastQueue();
    }

    private void handleCallHoldChanged(EventEnvelope<?> envelope) {
        CallHoldChangedPayload p = (CallHoldChangedPayload) envelope.payload();

        // Lua: EXISTS + HSET in single round-trip
        if (!callWriter.updateCallStateIfExists(p.callId(), p.newState())) {
            log.warn("CALL_HOLD_CHANGED for unknown call {}", p.callId());
        }

        broadcaster.broadcastCalls();
    }
}
