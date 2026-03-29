package com.fmr.ec3.oscc.state.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.ivr.IvrFlowPublishedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrFlowUnpublishedPayload;
import com.fmr.ec3.oscc.sender.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowKafkaEventHandler {

    private final EventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlowPublished(FlowPublishedEvent event) {
        try {
            String flowDefJson = objectMapper.writeValueAsString(event.definition());
            IvrFlowPublishedPayload payload = new IvrFlowPublishedPayload(
                    event.flowId().toString(),
                    flowDefJson,
                    event.version(),
                    System.currentTimeMillis());
            eventProducer.send(
                    KafkaTopics.IVR_FLOWS,
                    event.flowId().toString(),
                    EventType.IVR_FLOW_PUBLISHED,
                    "am-api",
                    event.flowId().toString(),
                    payload);
            log.info("Sent IVR_FLOW_PUBLISHED event for flow {} version {}",
                    event.flowId(), event.version());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize flow definition for Kafka event: {}",
                    event.flowId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlowUnpublished(FlowUnpublishedEvent event) {
        IvrFlowUnpublishedPayload payload = new IvrFlowUnpublishedPayload(
                event.flowId().toString(),
                System.currentTimeMillis());
        eventProducer.send(
                KafkaTopics.IVR_FLOWS,
                event.flowId().toString(),
                EventType.IVR_FLOW_UNPUBLISHED,
                "am-api",
                event.flowId().toString(),
                payload);
        log.info("Sent IVR_FLOW_UNPUBLISHED event for flow {}", event.flowId());
    }
}
