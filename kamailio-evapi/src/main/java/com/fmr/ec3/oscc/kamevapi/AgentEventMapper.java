package com.fmr.ec3.oscc.kamevapi;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.sip.AgentBreakEndedPayload;
import com.fmr.ec3.oscc.common.payload.sip.AgentBreakStartedPayload;
import com.fmr.ec3.oscc.common.payload.sip.AgentLoggedInPayload;
import com.fmr.ec3.oscc.common.payload.sip.AgentLoggedOutPayload;
import com.fmr.ec3.oscc.common.payload.sip.CallEndedPayload;
import com.fmr.ec3.oscc.common.payload.sip.CallRoutedToAgentPayload;

import java.util.List;
import java.util.Set;

/**
 * Maps raw Kamailio EVAPI events to am-common agent event types.
 */
public class AgentEventMapper {

    private static final Set<String> AVAILABLE_STATUSES = Set.of(
            "available", "online", "open");

    private static final Set<String> BREAK_STATUSES = Set.of(
            "away", "dnd", "busy", "lunch", "meeting");

    public record MappedEvent(String eventType, String agentId, Object payload) {}

    public MappedEvent map(String eventName, String aor, String status,
                           String reason, String callId, String caller, String serverId) {
        if (eventName == null || aor == null) {
            return null;
        }

        String agentId = extractAgentId(aor);
        if (agentId == null) {
            return null;
        }

        return switch (eventName) {
            case "usrloc:contact-insert", "usrloc:contact-update" ->
                    new MappedEvent(EventType.AGENT_LOGGED_IN, agentId,
                            new AgentLoggedInPayload(agentId, agentId, List.of(), serverId));

            case "usrloc:contact-delete" ->
                    new MappedEvent(EventType.AGENT_LOGGED_OUT, agentId,
                            new AgentLoggedOutPayload(agentId,
                                    reason != null ? reason : "unregistered", serverId));

            case "usrloc:contact-expire" ->
                    new MappedEvent(EventType.AGENT_LOGGED_OUT, agentId,
                            new AgentLoggedOutPayload(agentId, "expired", serverId));

            case "presence:update" -> mapPresence(agentId, status, serverId);

            case "dialog:start" ->
                    new MappedEvent(EventType.CALL_ROUTED_TO_AGENT, agentId,
                            new CallRoutedToAgentPayload(callId, agentId, agentId,
                                    caller != null ? caller : "", null, null,
                                    System.currentTimeMillis(), serverId));

            case "dialog:end" ->
                    new MappedEvent(EventType.CALL_ENDED, agentId,
                            new CallEndedPayload(callId, agentId,
                                    caller != null ? caller : "",
                                    0, System.currentTimeMillis(), 0,
                                    "normal", serverId));

            default -> null;
        };
    }

    String extractAgentId(String aor) {
        if (aor == null || aor.isBlank()) {
            return null;
        }

        String work = aor;
        // Strip sip: or sips: scheme
        if (work.startsWith("sip:")) {
            work = work.substring(4);
        } else if (work.startsWith("sips:")) {
            work = work.substring(5);
        }

        // Strip @domain
        int at = work.indexOf('@');
        if (at > 0) {
            work = work.substring(0, at);
        }

        return work.isBlank() ? null : work;
    }

    private MappedEvent mapPresence(String agentId, String status, String serverId) {
        if (status == null) {
            return null;
        }

        String normalized = status.toLowerCase();

        if (AVAILABLE_STATUSES.contains(normalized)) {
            return new MappedEvent(EventType.AGENT_BREAK_ENDED, agentId,
                    new AgentBreakEndedPayload(agentId, serverId));
        }

        if (BREAK_STATUSES.contains(normalized)) {
            return new MappedEvent(EventType.AGENT_BREAK_STARTED, agentId,
                    new AgentBreakStartedPayload(agentId, normalized, serverId));
        }

        return null;
    }
}
