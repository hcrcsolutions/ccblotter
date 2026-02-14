package com.fmr.ec3.oscc.receiver.listener;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.receiver.EventDeduplicator;
import com.fmr.ec3.oscc.receiver.handler.InfraEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class InfraHeartbeatListener {

    private static final Logger log = LoggerFactory.getLogger(InfraHeartbeatListener.class);

    private final EventDeduplicator deduplicator;
    private final InfraEventHandler handler;

    public InfraHeartbeatListener(EventDeduplicator deduplicator, InfraEventHandler handler) {
        this.deduplicator = deduplicator;
        this.handler = handler;
    }

    @KafkaListener(topics = KafkaTopics.INFRA_HEARTBEATS, groupId = "kafka-receiver",
            containerFactory = "osccKafkaListenerContainerFactory")
    public void onHeartbeat(@Payload EventEnvelope<?> envelope,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             Acknowledgment ack) {
        if (deduplicator.isDuplicate(partition, envelope.eventId())) {
            ack.acknowledge();
            return;
        }

        try {
            handler.handleHeartbeat(envelope);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to handle heartbeat {}: {}", envelope.eventId(), e.getMessage(), e);
            throw e;
        }
    }
}
