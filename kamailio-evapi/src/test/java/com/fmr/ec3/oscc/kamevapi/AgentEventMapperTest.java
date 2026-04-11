package com.fmr.ec3.oscc.kamevapi;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.sip.AgentBreakEndedPayload;
import com.fmr.ec3.oscc.common.payload.sip.AgentBreakStartedPayload;
import com.fmr.ec3.oscc.common.payload.sip.AgentLoggedInPayload;
import com.fmr.ec3.oscc.common.payload.sip.AgentLoggedOutPayload;
import com.fmr.ec3.oscc.common.payload.sip.CallEndedPayload;
import com.fmr.ec3.oscc.common.payload.sip.CallRoutedToAgentPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentEventMapperTest {

    private AgentEventMapper mapper;
    private static final String SERVER = "kam-1";

    @BeforeEach
    void setUp() {
        mapper = new AgentEventMapper();
    }

    // --- extractAgentId ---

    @Test
    void extractAgentIdFromSipUri() {
        assertEquals("agent1001", mapper.extractAgentId("sip:agent1001@sip.example.com"));
    }

    @Test
    void extractAgentIdFromSipsUri() {
        assertEquals("agent1001", mapper.extractAgentId("sips:agent1001@sip.example.com"));
    }

    @Test
    void extractAgentIdNoScheme() {
        assertEquals("agent1001", mapper.extractAgentId("agent1001@sip.example.com"));
    }

    @Test
    void extractAgentIdNoDomain() {
        assertEquals("agent1001", mapper.extractAgentId("sip:agent1001"));
    }

    @Test
    void extractAgentIdNull() {
        assertNull(mapper.extractAgentId(null));
    }

    @Test
    void extractAgentIdBlank() {
        assertNull(mapper.extractAgentId("  "));
    }

    // --- usrloc:contact-insert ---

    @Test
    void contactInsertMapsToLoggedIn() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "usrloc:contact-insert", "sip:agent1001@example.com",
                null, null, null, null, SERVER);

        assertNotNull(result);
        assertEquals(EventType.AGENT_LOGGED_IN, result.eventType());
        assertEquals("agent1001", result.agentId());
        AgentLoggedInPayload payload = (AgentLoggedInPayload) result.payload();
        assertEquals("agent1001", payload.agentId());
        assertEquals("agent1001", payload.agentName());
        assertEquals(List.of(), payload.skills());
        assertEquals(SERVER, payload.sipServerId());
    }

    // --- usrloc:contact-update ---

    @Test
    void contactUpdateMapsToLoggedIn() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "usrloc:contact-update", "sip:agent2002@example.com",
                null, null, null, null, SERVER);

        assertNotNull(result);
        assertEquals(EventType.AGENT_LOGGED_IN, result.eventType());
        assertEquals("agent2002", result.agentId());
    }

    // --- usrloc:contact-delete ---

    @Test
    void contactDeleteMapsToLoggedOutWithReason() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "usrloc:contact-delete", "sip:agent1001@example.com",
                null, "manual", null, null, SERVER);

        assertNotNull(result);
        assertEquals(EventType.AGENT_LOGGED_OUT, result.eventType());
        AgentLoggedOutPayload payload = (AgentLoggedOutPayload) result.payload();
        assertEquals("agent1001", payload.agentId());
        assertEquals("manual", payload.reason());
        assertEquals(SERVER, payload.sipServerId());
    }

    @Test
    void contactDeleteDefaultsReasonToUnregistered() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "usrloc:contact-delete", "sip:agent1001@example.com",
                null, null, null, null, SERVER);

        AgentLoggedOutPayload payload = (AgentLoggedOutPayload) result.payload();
        assertEquals("unregistered", payload.reason());
    }

    // --- usrloc:contact-expire ---

    @Test
    void contactExpireMapsToLoggedOutExpired() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "usrloc:contact-expire", "sip:agent1001@example.com",
                null, null, null, null, SERVER);

        assertNotNull(result);
        assertEquals(EventType.AGENT_LOGGED_OUT, result.eventType());
        AgentLoggedOutPayload payload = (AgentLoggedOutPayload) result.payload();
        assertEquals("expired", payload.reason());
    }

    // --- presence:update → BREAK_ENDED ---

    @ParameterizedTest
    @ValueSource(strings = {"available", "online", "open"})
    void presenceAvailableMapsToBreakEnded(String status) {
        AgentEventMapper.MappedEvent result = mapper.map(
                "presence:update", "sip:agent1001@example.com",
                status, null, null, null, SERVER);

        assertNotNull(result);
        assertEquals(EventType.AGENT_BREAK_ENDED, result.eventType());
        AgentBreakEndedPayload payload = (AgentBreakEndedPayload) result.payload();
        assertEquals("agent1001", payload.agentId());
        assertEquals(SERVER, payload.sipServerId());
    }

    // --- presence:update → BREAK_STARTED ---

    @ParameterizedTest
    @ValueSource(strings = {"away", "dnd", "busy", "lunch", "meeting"})
    void presenceBreakMapsToBreakStarted(String status) {
        AgentEventMapper.MappedEvent result = mapper.map(
                "presence:update", "sip:agent1001@example.com",
                status, null, null, null, SERVER);

        assertNotNull(result);
        assertEquals(EventType.AGENT_BREAK_STARTED, result.eventType());
        AgentBreakStartedPayload payload = (AgentBreakStartedPayload) result.payload();
        assertEquals("agent1001", payload.agentId());
        assertEquals(status, payload.breakType());
        assertEquals(SERVER, payload.sipServerId());
    }

    @Test
    void presenceCaseInsensitive() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "presence:update", "sip:agent1001@example.com",
                "AWAY", null, null, null, SERVER);

        assertNotNull(result);
        assertEquals(EventType.AGENT_BREAK_STARTED, result.eventType());
    }

    @Test
    void presenceUnknownStatusReturnsNull() {
        assertNull(mapper.map(
                "presence:update", "sip:agent1001@example.com",
                "unknown", null, null, null, SERVER));
    }

    @Test
    void presenceNullStatusReturnsNull() {
        assertNull(mapper.map(
                "presence:update", "sip:agent1001@example.com",
                null, null, null, null, SERVER));
    }

    // --- dialog:start ---

    @Test
    void dialogStartMapsToCallRouted() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "dialog:start", "sip:agent1001@example.com",
                null, null, "abc-123", "sip:+15551234567@carrier.com", SERVER);

        assertNotNull(result);
        assertEquals(EventType.CALL_ROUTED_TO_AGENT, result.eventType());
        CallRoutedToAgentPayload payload = (CallRoutedToAgentPayload) result.payload();
        assertEquals("abc-123", payload.callId());
        assertEquals("agent1001", payload.agentId());
        assertEquals("sip:+15551234567@carrier.com", payload.originator());
        assertEquals(SERVER, payload.sipServerId());
    }

    // --- dialog:end ---

    @Test
    void dialogEndMapsToCallEnded() {
        AgentEventMapper.MappedEvent result = mapper.map(
                "dialog:end", "sip:agent1001@example.com",
                null, null, "abc-123", "sip:+15551234567@carrier.com", SERVER);

        assertNotNull(result);
        assertEquals(EventType.CALL_ENDED, result.eventType());
        CallEndedPayload payload = (CallEndedPayload) result.payload();
        assertEquals("abc-123", payload.callId());
        assertEquals("agent1001", payload.agentId());
        assertEquals("normal", payload.reason());
        assertEquals(SERVER, payload.sipServerId());
    }

    // --- edge cases ---

    @Test
    void nullEventNameReturnsNull() {
        assertNull(mapper.map(null, "sip:agent1001@example.com",
                null, null, null, null, SERVER));
    }

    @Test
    void unknownEventNameReturnsNull() {
        assertNull(mapper.map("some:unknown", "sip:agent1001@example.com",
                null, null, null, null, SERVER));
    }

    @Test
    void nullAorReturnsNull() {
        assertNull(mapper.map("usrloc:contact-insert", null,
                null, null, null, null, SERVER));
    }
}
