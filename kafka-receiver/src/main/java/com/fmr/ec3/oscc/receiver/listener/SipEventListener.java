package com.fmr.ec3.oscc.receiver.listener;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.receiver.EventDeduplicator;
import com.fmr.ec3.oscc.receiver.handler.SipEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class SipEventListener {

    private static final Logger log = LoggerFactory.getLogger(SipEventListener.class);

    private final EventDeduplicator deduplicator;
    private final SipEventHandler handler;

    public SipEventListener(EventDeduplicator deduplicator, SipEventHandler handler) {
        this.deduplicator = deduplicator;
        this.handler = handler;
    }

    @KafkaListener(topics = KafkaTopics.SIP_EVENTS, groupId = "kafka-receiver")
    public void onSipEvent(@Payload EventEnvelope<?> envelope,
                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                            Acknowledgment ack) {
        if (deduplicator.isDuplicate(partition, envelope.eventId())) {
            log.debug("Duplicate event {} skipped", envelope.eventId());
            ack.acknowledge();
            return;
        }

        try {
            handler.handle(envelope);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to handle SIP event {}: {}", envelope.eventType(), e.getMessage(), e);
            // Don't ack - Kafka will redeliver
            throw e;
        }
    }
}
