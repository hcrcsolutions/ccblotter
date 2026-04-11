package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KamailioEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(KamailioEventSubscriber.class);

    private final EvApiConnectionManager connectionManager;
    private final EventProducer eventProducer;
    private final KamailioProperties props;
    private final AgentEventMapper mapper = new AgentEventMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KamailioEventSubscriber(EvApiConnectionManager connectionManager,
                                   EventProducer eventProducer,
                                   KamailioProperties props) {
        this.connectionManager = connectionManager;
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        connectionManager.setEventListener(this::onEvApiEvent);
        connectionManager.addOnConnectedCallback(this::onConnected);
        if (connectionManager.isConnected()) {
            onConnected();
        }
    }

    void onConnected() {
        log.info("EVAPI connected, ready for events from {}", props.getServerId());
    }

    void onEvApiEvent(String jsonLine) {
        JsonNode node;
        try {
            node = objectMapper.readTree(jsonLine);
        } catch (Exception e) {
            log.warn("Failed to parse EVAPI JSON: {}", e.getMessage());
            return;
        }

        String eventName = textOrNull(node, "event");
        if (eventName == null) {
            log.debug("EVAPI message missing 'event' field: {}", jsonLine);
            return;
        }

        // For dialog events, the agent AOR is in "callee"; for others it's in "aor"
        String aor = textOrNull(node, "aor");
        if (aor == null) {
            aor = textOrNull(node, "callee");
        }

        String status = textOrNull(node, "status");
        String reason = textOrNull(node, "reason");
        String callId = textOrNull(node, "callid");
        String caller = textOrNull(node, "caller");

        AgentEventMapper.MappedEvent mapped = mapper.map(
                eventName, aor, status, reason, callId, caller, props.getServerId());

        if (mapped == null) {
            log.debug("Unmapped EVAPI event: {}", eventName);
            return;
        }

        eventProducer.send(
                KafkaTopics.SIP_EVENTS,
                mapped.agentId(),
                mapped.eventType(),
                props.getServerId(),
                mapped.agentId(),
                mapped.payload());

        log.debug("Published {} for agent {}", mapped.eventType(), mapped.agentId());
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }
}
