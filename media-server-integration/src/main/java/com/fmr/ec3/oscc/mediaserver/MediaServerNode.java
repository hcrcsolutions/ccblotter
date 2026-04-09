package com.fmr.ec3.oscc.mediaserver;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.mediaserver.config.MediaServerProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MediaServerNode {

    private static final Logger log = LoggerFactory.getLogger(MediaServerNode.class);

    private final EventProducer eventProducer;
    private final MediaServerProperties props;
    private final Random random = new Random();

    public MediaServerNode(EventProducer eventProducer, MediaServerProperties props) {
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @PostConstruct
    public void register() {
        String nodeId = props.getNodeId();
        String hostname = String.format("%s.%s.example.com", nodeId, props.getDatacenter());
        String ipAddress = String.format("10.2.1.%d", Math.abs(nodeId.hashCode()) % 200 + 10);

        eventProducer.send(
            KafkaTopics.INFRA_LIFECYCLE, nodeId,
            EventType.NODE_REGISTERED, nodeId, nodeId,
            new NodeRegisteredPayload(
                nodeId, "MEDIA", hostname, ipAddress,
                props.getDatacenter(), props.getRegion(),
                props.getMaxSessions(), null, null)
        );

        log.info("Media server registered: {} in {} / {}", nodeId, props.getDatacenter(), props.getRegion());
    }

    @Scheduled(fixedDelayString = "${media-server.heartbeat-interval-ms:5000}", initialDelay = 3000)
    public void sendHeartbeat() {
        String nodeId = props.getNodeId();
        int maxSessions = props.getMaxSessions();
        int activeSessions = 50 + random.nextInt(maxSessions - 40);
        double utilization = (double) activeSessions / maxSessions;

        NodeMetricsDto metrics = new NodeMetricsDto(
            clamp(round(30 + utilization * 55 + random.nextGaussian() * 4), 0, 100),
            clamp(round(50 + utilization * 35 + random.nextGaussian() * 3), 0, 100),
            Math.max(1, (int) (3 + utilization * 30 + random.nextInt(10))),
            Math.max(1, (int) (utilization * 8 + random.nextInt(4))),
            clamp(round(random.nextDouble() * 0.05), 0, 100),
            clamp(round(utilization * 1.5 * random.nextDouble()), 0, 100),
            Math.max(15, 45 - (int) (utilization * 8))
        );

        // Media servers have higher session counts with inbound/outbound/transcoded breakdown
        int inbound = (int) (activeSessions * 0.55);
        int outbound = (int) (activeSessions * 0.35);
        int transcoded = (int) (activeSessions * 0.25);

        SessionBreakdownDto breakdown = new SessionBreakdownDto(
            inbound,
            outbound,
            transcoded,
            (int) (activeSessions * 0.05),
            (int) (activeSessions * 0.60),
            (int) (activeSessions * 0.08)
        );

        eventProducer.send(
            KafkaTopics.INFRA_HEARTBEATS, nodeId,
            EventType.NODE_HEARTBEAT, nodeId, nodeId,
            new NodeHeartbeatPayload(nodeId, "MEDIA", activeSessions, maxSessions, metrics, breakdown,
                null, null)
        );

        log.debug("Heartbeat sent: {} sessions={}/{} (in={}, out={}, xcode={})",
            nodeId, activeSessions, maxSessions, inbound, outbound, transcoded);
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
