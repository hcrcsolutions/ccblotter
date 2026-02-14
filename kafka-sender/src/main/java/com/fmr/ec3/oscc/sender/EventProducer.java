package com.fmr.ec3.oscc.sender;

import com.fmr.ec3.oscc.common.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EventProducer {

    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);

    private final KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public EventProducer(KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public <T> void send(String topic, String partitionKey, String eventType,
                         String sourceId, String correlationId, T payload) {
        EventEnvelope<T> envelope = new EventEnvelope<>(
            UUID.randomUUID().toString(),
            sequenceCounter.incrementAndGet(),
            sourceId,
            correlationId,
            partitionKey,
            eventType,
            System.currentTimeMillis(),
            1,
            payload
        );

        kafkaTemplate.send(topic, partitionKey, envelope)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send {} to {}: {}", eventType, topic, ex.getMessage());
                } else {
                    log.debug("Sent {} to {} partition {} offset {}",
                        eventType, topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
