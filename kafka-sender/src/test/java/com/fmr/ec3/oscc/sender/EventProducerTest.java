package com.fmr.ec3.oscc.sender;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.payload.sip.AgentLoggedInPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventProducerTest {

    @Mock
    private KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;

    private EventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new EventProducer(kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendCreatesEnvelopeWithCorrectFields() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0);
        SendResult<String, EventEnvelope<?>> sendResult = new SendResult<>(
            new ProducerRecord<>("topic", "key", null), metadata);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        var payload = new AgentLoggedInPayload("AGT-0001", "John", List.of("Sales"), "sip-1");
        producer.send("ccblotter.sip.events", "AGT-0001", EventType.AGENT_LOGGED_IN,
            "sip-sim-1", "AGT-0001", payload);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaTemplate).send(eq("ccblotter.sip.events"), eq("AGT-0001"), captor.capture());

        EventEnvelope<?> envelope = captor.getValue();
        assertNotNull(envelope.eventId());
        assertEquals(1L, envelope.sequenceNumber());
        assertEquals("sip-sim-1", envelope.sourceId());
        assertEquals("AGT-0001", envelope.correlationId());
        assertEquals("AGT-0001", envelope.partitionKey());
        assertEquals(EventType.AGENT_LOGGED_IN, envelope.eventType());
        assertTrue(envelope.timestamp() > 0);
        assertEquals(1, envelope.schemaVersion());
        assertSame(payload, envelope.payload());
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendIncrementsSequenceNumber() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0);
        SendResult<String, EventEnvelope<?>> sendResult = new SendResult<>(
            new ProducerRecord<>("topic", "key", null), metadata);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        var payload = new AgentLoggedInPayload("AGT-0001", "John", List.of("Sales"), "sip-1");

        producer.send("t", "k", "TYPE", "src", "corr", payload);
        producer.send("t", "k", "TYPE", "src", "corr", payload);
        producer.send("t", "k", "TYPE", "src", "corr", payload);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaTemplate, times(3)).send(eq("t"), eq("k"), captor.capture());

        var envelopes = captor.getAllValues();
        assertEquals(1L, envelopes.get(0).sequenceNumber());
        assertEquals(2L, envelopes.get(1).sequenceNumber());
        assertEquals(3L, envelopes.get(2).sequenceNumber());
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendGeneratesUniqueEventIds() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("topic", 0), 0, 0, 0, 0, 0);
        SendResult<String, EventEnvelope<?>> sendResult = new SendResult<>(
            new ProducerRecord<>("topic", "key", null), metadata);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        var payload = new AgentLoggedInPayload("AGT-0001", "John", List.of("Sales"), "sip-1");

        producer.send("t", "k", "TYPE", "src", "corr", payload);
        producer.send("t", "k", "TYPE", "src", "corr", payload);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(kafkaTemplate, times(2)).send(eq("t"), eq("k"), captor.capture());

        assertNotEquals(captor.getAllValues().get(0).eventId(), captor.getAllValues().get(1).eventId());
    }
}
