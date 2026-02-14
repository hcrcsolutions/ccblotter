package com.fmr.ec3.oscc.common.serde;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.infra.*;
import com.fmr.ec3.oscc.common.payload.sip.*;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class EventEnvelopeDeserializer implements Deserializer<EventEnvelope<?>> {

    private final ObjectMapper objectMapper;

    private static final Map<String, Class<?>> PAYLOAD_TYPES = Map.ofEntries(
        Map.entry(EventType.AGENT_LOGGED_IN, AgentLoggedInPayload.class),
        Map.entry(EventType.AGENT_LOGGED_OUT, AgentLoggedOutPayload.class),
        Map.entry(EventType.AGENT_BREAK_STARTED, AgentBreakStartedPayload.class),
        Map.entry(EventType.AGENT_BREAK_ENDED, AgentBreakEndedPayload.class),
        Map.entry(EventType.CALL_QUEUED, CallQueuedPayload.class),
        Map.entry(EventType.CALL_ROUTED_TO_AGENT, CallRoutedToAgentPayload.class),
        Map.entry(EventType.CALL_ENDED, CallEndedPayload.class),
        Map.entry(EventType.CALL_ABANDONED, CallAbandonedPayload.class),
        Map.entry(EventType.CALL_HOLD_CHANGED, CallHoldChangedPayload.class),
        Map.entry(EventType.NODE_REGISTERED, NodeRegisteredPayload.class),
        Map.entry(EventType.NODE_HEARTBEAT, NodeHeartbeatPayload.class),
        Map.entry(EventType.NODE_DEREGISTERED, NodeDeregisteredPayload.class),
        Map.entry(EventType.NODE_ALARM, NodeAlarmPayload.class)
    );

    public EventEnvelopeDeserializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public EventEnvelope<?> deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.readTree(data);
            JsonNode eventTypeNode = tree.get("eventType");
            if (eventTypeNode == null) {
                throw new IllegalArgumentException("EventEnvelope missing required 'eventType' field");
            }
            String eventType = eventTypeNode.asText();
            Class<?> payloadClass = PAYLOAD_TYPES.getOrDefault(eventType, Object.class);

            JavaType envelopeType = objectMapper.getTypeFactory()
                    .constructParametricType(EventEnvelope.class, payloadClass);

            return objectMapper.readValue(data, envelopeType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize EventEnvelope", e);
        }
    }
}
