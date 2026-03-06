package com.fmr.ec3.oscc.sipserver;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sipserver.config.SipServerProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class SipServerNode {

    private static final Logger log = LoggerFactory.getLogger(SipServerNode.class);

    private final EventProducer eventProducer;
    private final SipServerProperties props;
    private final InteractionStore interactionStore;
    private final Random random = new Random();

    public SipServerNode(EventProducer eventProducer,
                         SipServerProperties props,
                         InteractionStore interactionStore) {
        this.eventProducer = eventProducer;
        this.props = props;
        this.interactionStore = interactionStore;
    }

    @PostConstruct
    public void register() {
        String nodeId = props.getNodeId();
        String hostname = String.format("%s.%s.example.com", nodeId, props.getDatacenter());
        String ipAddress = String.format("10.1.1.%d", Math.abs(nodeId.hashCode()) % 200 + 10);

        eventProducer.send(
            KafkaTopics.INFRA_LIFECYCLE, nodeId,
            EventType.NODE_REGISTERED, nodeId, nodeId,
            new NodeRegisteredPayload(
                nodeId, "SIP", hostname, ipAddress,
                props.getDatacenter(), props.getRegion(),
                props.getMaxSessions(), null, null)
        );

        log.info("SIP server registered: {} in {} / {}", nodeId, props.getDatacenter(), props.getRegion());
    }

    @Scheduled(fixedDelayString = "${sip-server.heartbeat-interval-ms:5000}", initialDelay = 3000)
    public void sendHeartbeat() {
        String nodeId = props.getNodeId();
        int maxSessions = props.getMaxSessions();
        int activeSessions = interactionStore.getActiveSessions();
        double utilization = maxSessions > 0
                ? (double) activeSessions / maxSessions : 0;

        NodeMetricsDto metrics = new NodeMetricsDto(
            clamp(round(20 + utilization * 50 + random.nextGaussian() * 5), 0, 100),
            clamp(round(40 + utilization * 30 + random.nextGaussian() * 3), 0, 100),
            Math.max(1, (int) (5 + utilization * 50 + random.nextInt(20))),
            Math.max(1, (int) (utilization * 10 + random.nextInt(5))),
            clamp(round(random.nextDouble() * 0.1), 0, 100),
            clamp(round(utilization * 2 * random.nextDouble()), 0, 100),
            Math.max(10, 45 - (int) (utilization * 10))
        );

        SessionBreakdownDto breakdown = interactionStore.getSessionBreakdown();

        eventProducer.send(
            KafkaTopics.INFRA_HEARTBEATS, nodeId,
            EventType.NODE_HEARTBEAT, nodeId, nodeId,
            new NodeHeartbeatPayload(nodeId, "SIP", activeSessions, maxSessions, metrics, breakdown)
        );

        log.debug("Heartbeat sent: {} sessions={}/{}", nodeId, activeSessions, maxSessions);
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
