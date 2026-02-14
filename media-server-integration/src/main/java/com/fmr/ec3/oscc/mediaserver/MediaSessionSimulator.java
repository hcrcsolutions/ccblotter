package com.fmr.ec3.oscc.mediaserver;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.mediaserver.config.MediaServerProperties;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class MediaSessionSimulator {

    private static final Logger log = LoggerFactory.getLogger(MediaSessionSimulator.class);

    private final EventProducer eventProducer;
    private final MediaServerProperties props;
    private final Random random = new Random();

    private int currentLoad = 50;

    public MediaSessionSimulator(EventProducer eventProducer, MediaServerProperties props) {
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${media-server.session-sim-interval-ms:4000}", initialDelay = 8000)
    public void simulateSessionLoad() {
        String nodeId = props.getNodeId();
        int maxSessions = props.getMaxSessions();

        // Drift the load up or down by a small amount
        int drift = random.nextInt(11) - 5;
        currentLoad = Math.max(20, Math.min(maxSessions, currentLoad + drift));

        double utilization = (double) currentLoad / maxSessions;

        // Simulate RTP stream counts and transcoding load
        int rtpStreams = currentLoad * 2;
        int transcodingSessions = (int) (currentLoad * 0.25 + random.nextInt(5));
        int conferenceLegs = random.nextInt(Math.max(1, currentLoad / 10));

        NodeMetricsDto metrics = new NodeMetricsDto(
            clamp(round(35 + utilization * 50 + random.nextGaussian() * 3), 0, 100),
            clamp(round(45 + utilization * 40 + random.nextGaussian() * 2), 0, 100),
            Math.max(1, (int) (2 + utilization * 20 + random.nextInt(8))),
            Math.max(1, (int) (utilization * 6 + random.nextInt(3))),
            clamp(round(random.nextDouble() * 0.03), 0, 100),
            clamp(round(utilization * random.nextDouble()), 0, 100),
            Math.max(20, 45 - (int) (utilization * 6))
        );

        SessionBreakdownDto breakdown = new SessionBreakdownDto(
            (int) (currentLoad * 0.5),
            (int) (currentLoad * 0.4),
            transcodingSessions,
            conferenceLegs,
            (int) (currentLoad * 0.55),
            (int) (currentLoad * 0.05)
        );

        eventProducer.send(
            KafkaTopics.INFRA_HEARTBEATS, nodeId,
            EventType.NODE_HEARTBEAT, nodeId + "-session-sim", nodeId,
            new NodeHeartbeatPayload(nodeId, "MEDIA", currentLoad, maxSessions, metrics, breakdown)
        );

        log.debug("Session load: {} sessions={}/{} rtp={} xcode={} conf={}",
            nodeId, currentLoad, maxSessions, rtpStreams, transcodingSessions, conferenceLegs);
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
