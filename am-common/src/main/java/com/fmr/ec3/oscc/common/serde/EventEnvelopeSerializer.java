package com.fmr.ec3.oscc.common.serde;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serializer;

public class EventEnvelopeSerializer implements Serializer<EventEnvelope<?>> {

    private final ObjectMapper objectMapper;

    public EventEnvelopeSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public byte[] serialize(String topic, EventEnvelope<?> data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EventEnvelope", e);
        }
    }
}
