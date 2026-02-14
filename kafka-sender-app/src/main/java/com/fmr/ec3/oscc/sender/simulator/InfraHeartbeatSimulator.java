package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class InfraHeartbeatSimulator {

    private static final Logger log = LoggerFactory.getLogger(InfraHeartbeatSimulator.class);

    private final EventProducer eventProducer;
    private final InfraTopologyBootstrap topologyBootstrap;
    private final Random random = new Random();

    public InfraHeartbeatSimulator(EventProducer eventProducer,
                                    InfraTopologyBootstrap topologyBootstrap) {
        this.eventProducer = eventProducer;
        this.topologyBootstrap = topologyBootstrap;
    }

    @Scheduled(fixedDelayString = "${simulation.heartbeat-interval-ms:5000}", initialDelay = 5000)
    public void sendHeartbeats() {
        for (String nodeId : topologyBootstrap.getRegisteredNodeIds()) {
            boolean isSip = nodeId.startsWith("sip-");
            int maxSessions = isSip ? 500 : 100;
            String nodeType = isSip ? "SIP" : "MEDIA";

            int activeSessions = random.nextInt(maxSessions + 1);
            double utilization = (double) activeSessions / maxSessions;

            NodeMetricsDto metrics = new NodeMetricsDto(
                clamp(round(20 + utilization * 50 + random.nextGaussian() * 5), 0, 100),
                clamp(round(40 + utilization * 30 + random.nextGaussian() * 3), 0, 100),
                Math.max(1, (int) (5 + utilization * 50 + random.nextInt(20))),
                Math.max(1, (int) (utilization * 10 + random.nextInt(5))),
                clamp(round(random.nextDouble() * 0.1), 0, 100),
                clamp(round(utilization * 2 * random.nextDouble()), 0, 100),
                Math.max(10, 45 - (int) (utilization * 10))
            );

            SessionBreakdownDto breakdown = new SessionBreakdownDto(
                (int) (activeSessions * 0.7),
                (int) (activeSessions * 0.3),
                (int) (activeSessions * 0.1),
                (int) (activeSessions * 0.15),
                (int) (activeSessions * 0.65),
                (int) (activeSessions * 0.1)
            );

            eventProducer.send(
                KafkaTopics.INFRA_HEARTBEATS, nodeId,
                EventType.NODE_HEARTBEAT, nodeId, nodeId,
                new NodeHeartbeatPayload(nodeId, nodeType, activeSessions, maxSessions, metrics, breakdown)
            );
        }

        log.debug("Sent heartbeats for {} nodes", topologyBootstrap.getRegisteredNodeIds().size());
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
