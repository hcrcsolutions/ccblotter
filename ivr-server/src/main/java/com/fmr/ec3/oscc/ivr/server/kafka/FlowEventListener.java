package com.fmr.ec3.oscc.ivr.server.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.ivr.IvrFlowPublishedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrFlowUnpublishedPayload;
import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.server.service.FlowCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FlowEventListener {

    private final FlowCacheService flowCacheService;
    private final ObjectMapper objectMapper;

    public FlowEventListener(FlowCacheService flowCacheService, ObjectMapper objectMapper) {
        this.flowCacheService = flowCacheService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.IVR_FLOWS,
                   containerFactory = "ivrFlowListenerContainerFactory")
    public void onFlowEvent(ConsumerRecord<String, EventEnvelope<?>> record,
                            Acknowledgment ack) {
        EventEnvelope<?> envelope = record.value();
        log.debug("Received flow event: type={} key={}", envelope.eventType(), record.key());

        switch (envelope.eventType()) {
            case EventType.IVR_FLOW_PUBLISHED -> handlePublished(envelope);
            case EventType.IVR_FLOW_UNPUBLISHED -> handleUnpublished(envelope);
            default -> log.warn("Unknown flow event type: {}", envelope.eventType());
        }

        ack.acknowledge();
    }

    private void handlePublished(EventEnvelope<?> envelope) {
        IvrFlowPublishedPayload payload = (IvrFlowPublishedPayload) envelope.payload();
        try {
            FlowDefinition flow = objectMapper.readValue(
                    payload.flowDefinitionJson(), FlowDefinition.class);
            flowCacheService.put(flow);
            log.info("Cached published flow '{}' (id={}, version={})",
                    flow.getName(), payload.flowId(), payload.version());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize flow definition for flowId={}", payload.flowId(), e);
            throw new RuntimeException("Failed to deserialize flow definition", e);
        }
    }

    private void handleUnpublished(EventEnvelope<?> envelope) {
        IvrFlowUnpublishedPayload payload = (IvrFlowUnpublishedPayload) envelope.payload();
        boolean removed = flowCacheService.removeIfPresent(payload.flowId());
        if (removed) {
            log.info("Removed unpublished flow from cache: {}", payload.flowId());
        } else {
            log.debug("Flow not in cache, nothing to remove: {}", payload.flowId());
        }
    }
}
