package com.fmr.ec3.oscc.common.serde;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.infra.*;
import com.fmr.ec3.oscc.common.payload.sip.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import org.apache.kafka.common.errors.SerializationException;

import static org.junit.jupiter.api.Assertions.*;

class EventEnvelopeSerdeTest {

    private EventEnvelopeSerializer serializer;
    private EventEnvelopeDeserializer deserializer;

    @BeforeEach
    void setUp() {
        serializer = new EventEnvelopeSerializer();
        deserializer = new EventEnvelopeDeserializer();
    }

    @Test
    void serializeNullReturnsNull() {
        assertNull(serializer.serialize("topic", null));
    }

    @Test
    void deserializeNullReturnsNull() {
        assertNull(deserializer.deserialize("topic", null));
    }

    @Test
    void roundTripAgentLoggedIn() {
        var payload = new AgentLoggedInPayload("AGT-0001", "John Smith", List.of("Sales", "Support"), "sip-1");
        var envelope = envelope(EventType.AGENT_LOGGED_IN, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        assertEnvelopeMetadata(envelope, result);
        assertInstanceOf(AgentLoggedInPayload.class, result.payload());
        var p = (AgentLoggedInPayload) result.payload();
        assertEquals("AGT-0001", p.agentId());
        assertEquals("John Smith", p.agentName());
        assertEquals(List.of("Sales", "Support"), p.skills());
        assertEquals("sip-1", p.sipServerId());
    }

    @Test
    void roundTripAgentLoggedOut() {
        var payload = new AgentLoggedOutPayload("AGT-0002", "end_of_shift", "sip-1");
        var envelope = envelope(EventType.AGENT_LOGGED_OUT, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (AgentLoggedOutPayload) result.payload();
        assertEquals("AGT-0002", p.agentId());
        assertEquals("end_of_shift", p.reason());
    }

    @Test
    void roundTripAgentBreakStarted() {
        var payload = new AgentBreakStartedPayload("AGT-0003", "Lunch", "sip-2");
        var envelope = envelope(EventType.AGENT_BREAK_STARTED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (AgentBreakStartedPayload) result.payload();
        assertEquals("AGT-0003", p.agentId());
        assertEquals("Lunch", p.breakType());
    }

    @Test
    void roundTripAgentBreakEnded() {
        var payload = new AgentBreakEndedPayload("AGT-0003", "sip-2");
        var envelope = envelope(EventType.AGENT_BREAK_ENDED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (AgentBreakEndedPayload) result.payload();
        assertEquals("AGT-0003", p.agentId());
    }

    @Test
    void roundTripCallQueued() {
        var payload = new CallQueuedPayload("call-1", "(212) 555-0100", "Sales", 2, 1700000000000L, "sip-1");
        var envelope = envelope(EventType.CALL_QUEUED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (CallQueuedPayload) result.payload();
        assertEquals("call-1", p.callId());
        assertEquals("(212) 555-0100", p.originator());
        assertEquals("Sales", p.skill());
        assertEquals(2, p.priority());
        assertEquals(1700000000000L, p.queuedAtMs());
    }

    @Test
    void roundTripCallRoutedToAgent() {
        var payload = new CallRoutedToAgentPayload(
            "call-2", "AGT-0001", "John Smith", "(312) 555-0200",
            "queued-1", "Support", 1700000001000L, "sip-1");
        var envelope = envelope(EventType.CALL_ROUTED_TO_AGENT, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (CallRoutedToAgentPayload) result.payload();
        assertEquals("call-2", p.callId());
        assertEquals("AGT-0001", p.agentId());
        assertEquals("John Smith", p.agentName());
        assertEquals("(312) 555-0200", p.originator());
        assertEquals("queued-1", p.queuedCallId());
        assertEquals("Support", p.skill());
        assertEquals(1700000001000L, p.callStartTimeMs());
    }

    @Test
    void roundTripCallEnded() {
        var payload = new CallEndedPayload(
            "call-2", "AGT-0001", "(312) 555-0200",
            1700000001000L, 1700000301000L, 300, "normal_clearing", "sip-1");
        var envelope = envelope(EventType.CALL_ENDED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (CallEndedPayload) result.payload();
        assertEquals("call-2", p.callId());
        assertEquals(300, p.durationSeconds());
        assertEquals("normal_clearing", p.reason());
    }

    @Test
    void roundTripCallAbandoned() {
        var payload = new CallAbandonedPayload(
            "call-3", "(415) 555-0300", 1700000000000L, 1700000060000L, 60, "sip-1");
        var envelope = envelope(EventType.CALL_ABANDONED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (CallAbandonedPayload) result.payload();
        assertEquals("call-3", p.callId());
        assertEquals(60, p.waitDurationSeconds());
    }

    @Test
    void roundTripCallHoldChanged() {
        var payload = new CallHoldChangedPayload("call-2", "AGT-0001", "ON_HOLD", "sip-1");
        var envelope = envelope(EventType.CALL_HOLD_CHANGED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (CallHoldChangedPayload) result.payload();
        assertEquals("ON_HOLD", p.newState());
    }

    @Test
    void roundTripNodeRegistered() {
        var payload = new NodeRegisteredPayload(
            "sip-1", "SIP", "sip-01.dc1.example.com", "10.1.1.10",
            "dc1", "us-east", 500, null, null);
        var envelope = envelope(EventType.NODE_REGISTERED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (NodeRegisteredPayload) result.payload();
        assertEquals("sip-1", p.nodeId());
        assertEquals("SIP", p.nodeType());
        assertEquals(500, p.maxSessions());
        assertNull(p.carrierName());
    }

    @Test
    void roundTripNodeHeartbeat() {
        var metrics = new NodeMetricsDto(45.5, 62.3, 12, 3, 0.05, 0.1, 42);
        var breakdown = new SessionBreakdownDto(70, 30, 10, 15, 65, 10);
        var payload = new NodeHeartbeatPayload("media-1", "MEDIA", 80, 100, metrics, breakdown,
            null, null);
        var envelope = envelope(EventType.NODE_HEARTBEAT, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (NodeHeartbeatPayload) result.payload();
        assertEquals("media-1", p.nodeId());
        assertEquals(80, p.activeSessions());
        assertEquals(45.5, p.metrics().cpuPercent());
        assertEquals(70, p.sessionBreakdown().inboundSessions());
    }

    @Test
    void roundTripNodeDeregistered() {
        var payload = new NodeDeregisteredPayload("media-5", "shutdown");
        var envelope = envelope(EventType.NODE_DEREGISTERED, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (NodeDeregisteredPayload) result.payload();
        assertEquals("media-5", p.nodeId());
        assertEquals("shutdown", p.reason());
    }

    @Test
    void roundTripNodeAlarm() {
        var payload = new NodeAlarmPayload("sip-2", "CPU_HIGH", "WARNING", 80.0, 92.5);
        var envelope = envelope(EventType.NODE_ALARM, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        var p = (NodeAlarmPayload) result.payload();
        assertEquals("sip-2", p.nodeId());
        assertEquals("CPU_HIGH", p.alarmType());
        assertEquals(92.5, p.actualValue());
    }

    @Test
    void envelopeFieldsPreserved() {
        var payload = new AgentBreakEndedPayload("AGT-0001", "sip-1");
        var envelope = new EventEnvelope<>(
            "evt-123", 42L, "sip-sim-1", "corr-456", "AGT-0001",
            EventType.AGENT_BREAK_ENDED, 1700000000000L, 1, payload);

        EventEnvelope<?> result = roundTrip(envelope);

        assertEquals("evt-123", result.eventId());
        assertEquals(42L, result.sequenceNumber());
        assertEquals("sip-sim-1", result.sourceId());
        assertEquals("corr-456", result.correlationId());
        assertEquals("AGT-0001", result.partitionKey());
        assertEquals(EventType.AGENT_BREAK_ENDED, result.eventType());
        assertEquals(1700000000000L, result.timestamp());
        assertEquals(1, result.schemaVersion());
    }

    @Test
    void deserializeMissingEventTypeThrows() {
        byte[] json = "{\"eventId\":\"evt-1\",\"payload\":{}}".getBytes();
        var ex = assertThrows(SerializationException.class,
            () -> deserializer.deserialize("topic", json));
        assertTrue(ex.getCause().getMessage().contains("eventType"));
    }

    @Test
    void unknownEventTypeDeserializesToObjectPayload() {
        var envelope = new EventEnvelope<>(
            "evt-1", 1L, "src", "corr", "key",
            "UNKNOWN_EVENT", System.currentTimeMillis(), 1,
            java.util.Map.of("foo", "bar"));

        byte[] bytes = serializer.serialize("topic", envelope);
        EventEnvelope<?> result = deserializer.deserialize("topic", bytes);

        assertEquals("UNKNOWN_EVENT", result.eventType());
        assertNotNull(result.payload());
    }

    private <T> EventEnvelope<T> envelope(String eventType, T payload) {
        return new EventEnvelope<>(
            "evt-" + System.nanoTime(), 1L, "sip-sim-1", "corr-1", "key-1",
            eventType, System.currentTimeMillis(), 1, payload);
    }

    private EventEnvelope<?> roundTrip(EventEnvelope<?> envelope) {
        byte[] bytes = serializer.serialize("topic", envelope);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        return deserializer.deserialize("topic", bytes);
    }

    private void assertEnvelopeMetadata(EventEnvelope<?> expected, EventEnvelope<?> actual) {
        assertEquals(expected.eventId(), actual.eventId());
        assertEquals(expected.sequenceNumber(), actual.sequenceNumber());
        assertEquals(expected.sourceId(), actual.sourceId());
        assertEquals(expected.eventType(), actual.eventType());
        assertEquals(expected.schemaVersion(), actual.schemaVersion());
    }
}
