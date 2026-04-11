package com.fmr.ec3.oscc.kamevapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.kamevapi.config.KamailioProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        name = "kamailio.state-sync-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class KamailioStateSync {

    private static final Logger log = LoggerFactory.getLogger(KamailioStateSync.class);

    private final EvApiConnectionManager connectionManager;
    private final EventProducer eventProducer;
    private final KamailioProperties props;
    private final AgentEventMapper mapper = new AgentEventMapper();

    public KamailioStateSync(EvApiConnectionManager connectionManager,
                             EventProducer eventProducer,
                             KamailioProperties props) {
        this.connectionManager = connectionManager;
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        connectionManager.addOnConnectedCallback(this::syncAll);
    }

    @Scheduled(
            fixedDelayString = "${kamailio.state-sync-interval-ms:30000}",
            initialDelayString = "${kamailio.state-sync-interval-ms:30000}")
    public void scheduledSync() {
        if (connectionManager.isConnected()) {
            syncAll();
        }
    }

    void syncAll() {
        log.debug("Starting state sync from {}", props.getServerId());
        syncUsrloc();
        syncPresence();
        syncDialogs();
        log.debug("State sync complete for {}", props.getServerId());
    }

    void syncUsrloc() {
        try {
            JsonNode result = connectionManager
                    .sendRequest(props.getRpcMethodUsrloc(), null)
                    .get(props.getStateSyncRpcTimeoutMs(), TimeUnit.MILLISECONDS);
            if (result == null || !result.isArray()) {
                return;
            }
            for (JsonNode entry : result) {
                String aor = textOrNull(entry, "aor");
                if (aor == null) {
                    continue;
                }
                AgentEventMapper.MappedEvent mapped = mapper.map(
                        "usrloc:contact-insert", aor, null, null,
                        null, null, props.getServerId());
                if (mapped != null) {
                    sendEvent(mapped);
                }
            }
        } catch (Exception e) {
            log.warn("syncUsrloc failed: {}", e.getMessage());
        }
    }

    void syncPresence() {
        try {
            JsonNode result = connectionManager
                    .sendRequest(props.getRpcMethodPresence(), null)
                    .get(props.getStateSyncRpcTimeoutMs(), TimeUnit.MILLISECONDS);
            if (result == null || !result.isArray()) {
                return;
            }
            for (JsonNode entry : result) {
                String aor = textOrNull(entry, "aor");
                String status = textOrNull(entry, "status");
                if (aor == null || status == null) {
                    continue;
                }
                AgentEventMapper.MappedEvent mapped = mapper.map(
                        "presence:update", aor, status, null,
                        null, null, props.getServerId());
                if (mapped != null) {
                    sendEvent(mapped);
                }
            }
        } catch (Exception e) {
            log.warn("syncPresence failed: {}", e.getMessage());
        }
    }

    void syncDialogs() {
        try {
            JsonNode result = connectionManager
                    .sendRequest(props.getRpcMethodDialog(), null)
                    .get(props.getStateSyncRpcTimeoutMs(), TimeUnit.MILLISECONDS);
            if (result == null || !result.isArray()) {
                return;
            }
            for (JsonNode entry : result) {
                String callee = textOrNull(entry, "callee");
                String callId = textOrNull(entry, "callid");
                String caller = textOrNull(entry, "caller");
                if (callee == null) {
                    continue;
                }
                AgentEventMapper.MappedEvent mapped = mapper.map(
                        "dialog:start", callee, null, null,
                        callId, caller, props.getServerId());
                if (mapped != null) {
                    sendEvent(mapped);
                }
            }
        } catch (Exception e) {
            log.warn("syncDialogs failed: {}", e.getMessage());
        }
    }

    private void sendEvent(AgentEventMapper.MappedEvent mapped) {
        eventProducer.send(
                KafkaTopics.SIP_EVENTS,
                mapped.agentId(),
                mapped.eventType(),
                props.getServerId(),
                mapped.agentId(),
                mapped.payload());
        log.debug("State sync: published {} for agent {}",
                mapped.eventType(), mapped.agentId());
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }
}
