package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.dto.request.NodeRegistrationRequest;
import com.fmr.ec3.oscc.state.entity.NodeEntity;
import com.fmr.ec3.oscc.state.model.InfraServerType;
import com.fmr.ec3.oscc.state.model.NodeMetrics;
import com.fmr.ec3.oscc.state.model.ServerHealthStatus;
import com.fmr.ec3.oscc.state.model.SessionBreakdown;
import com.fmr.ec3.oscc.state.repository.NodeRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mock-data.enabled", havingValue = "true")
public class MockDataGeneratorService {

    private final NodeRegistrationService registrationService;
    private final EdgeManagementService edgeService;
    private final NodeRepository nodeRepository;
    private final RedisStateService redisStateService;

    private final Random random = new Random();
    private volatile boolean initialized = false;

    private static final String[] DATACENTERS = {"dc1", "dc2", "dc3", "dc4"};
    private static final String[] REGIONS = {"us-east", "us-east", "us-west", "us-west"};

    @PostConstruct
    public void initialize() {
        if (nodeRepository.count() == 0) {
            log.info("No nodes found, generating mock topology...");
            generateMockTopology();
        } else {
            log.info("Nodes exist ({}), skipping mock generation", nodeRepository.count());
        }
        initialized = true;
    }

    /**
     * Simulate heartbeats and metric updates every 5 seconds.
     * Uses batch DB update and updates Redis directly for efficiency.
     */
    @Scheduled(fixedRate = 5000, initialDelay = 10000)
    @Transactional
    public void simulateHeartbeats() {
        if (!initialized) {
            return;
        }

        Instant now = Instant.now();

        // Batch update all healthy nodes' heartbeat timestamp in a single query
        int updated = nodeRepository.updateHeartbeatTimestampBatch(now, ServerHealthStatus.HEALTHY);

        // Update Redis state for each node (lightweight in-memory updates)
        List<NodeEntity> nodes = nodeRepository.findByHealthStatus(ServerHealthStatus.HEALTHY);
        for (NodeEntity node : nodes) {
            simulateNodeMetrics(node);
        }

        log.debug("Simulated heartbeats for {} nodes (batch DB update)", updated);
    }

    /**
     * Simulate metrics for a node - only updates Redis, not the database.
     */
    private void simulateNodeMetrics(NodeEntity node) {
        String nodeId = node.getId();
        int maxSessions = node.getMaxSessions();

        // Guard against maxSessions <= 0
        if (maxSessions <= 0) {
            maxSessions = 1;
        }

        int activeSessions = random.nextInt(maxSessions + 1);
        double utilizationFactor = (double) activeSessions / maxSessions;

        NodeMetrics metrics = NodeMetrics.builder()
                .cpuPercent(clamp(round(20 + utilizationFactor * 50 + random.nextGaussian() * 5), 0, 100))
                .memoryPercent(clamp(round(40 + utilizationFactor * 30 + random.nextGaussian() * 3), 0, 100))
                .latencyMs(Math.max(1, (int) (5 + utilizationFactor * 50 + random.nextInt(20))))
                .jitterMs(Math.max(1, (int) (utilizationFactor * 10 + random.nextInt(5))))
                .packetLossPercent(clamp(round(random.nextDouble() * 0.1), 0, 100))
                .errorRate(clamp(round(utilizationFactor * 2 * random.nextDouble()), 0, 100))
                .mosScore(Math.max(10, 45 - (int) (utilizationFactor * 10)))
                .build();

        SessionBreakdown breakdown = SessionBreakdown.builder()
                .inboundSessions((int) (activeSessions * 0.7))
                .outboundSessions((int) (activeSessions * 0.3))
                .ivrSessions((int) (activeSessions * 0.1))
                .queueSessions((int) (activeSessions * 0.15))
                .agentSessions((int) (activeSessions * 0.65))
                .onHoldSessions((int) (activeSessions * 0.1))
                .build();

        try {
            // Update Redis only - no DB calls
            redisStateService.updateHeartbeat(nodeId, Instant.now());
            redisStateService.setNodeMetrics(nodeId, metrics);
            redisStateService.setSessionCounts(nodeId, activeSessions, breakdown);
            redisStateService.addTrendDataPoint(nodeId, activeSessions, metrics.getCpuPercent());
        } catch (Exception e) {
            log.warn("Failed to update Redis for {}: {}", nodeId, e.getMessage());
        }
    }

    private void generateMockTopology() {
        // Register SIP servers
        for (int i = 1; i <= 4; i++) {
            int dcIdx = i - 1;
            registerNode("sip-" + i, InfraServerType.SIP,
                    String.format("sip-%02d.%s.example.com", i, DATACENTERS[dcIdx]),
                    String.format("10.%d.1.10", i),
                    DATACENTERS[dcIdx], REGIONS[dcIdx], 500);
        }

        // Register Media servers
        for (int i = 1; i <= 120; i++) {
            int dcIdx = (i - 1) / 30;
            registerNode("media-" + i, InfraServerType.MEDIA,
                    String.format("media-%03d.%s.example.com", i, DATACENTERS[dcIdx]),
                    String.format("10.%d.%d.%d", dcIdx + 1, 2 + (i % 10), 10 + ((i - 1) % 30)),
                    DATACENTERS[dcIdx], REGIONS[dcIdx], 100);
        }

        // Create SIP -> Media connections
        for (int sipNum = 1; sipNum <= 4; sipNum++) {
            String sipId = "sip-" + sipNum;
            int mediaStart = (sipNum - 1) * 30 + 1;
            int mediaEnd = sipNum * 30;

            List<String> mediaIds = new ArrayList<>();
            for (int m = mediaStart; m <= mediaEnd; m++) {
                mediaIds.add("media-" + m);
            }

            edgeService.addConnectionsBulk(sipId, mediaIds);
            log.info("Created {} connections for {}", mediaIds.size(), sipId);
        }

        log.info("Mock topology generated: {} nodes", nodeRepository.count());
    }

    private void registerNode(String id, InfraServerType type, String hostname,
                              String ip, String datacenter, String region, int maxSessions) {
        NodeRegistrationRequest request = NodeRegistrationRequest.builder()
                .id(id)
                .type(type)
                .hostname(hostname)
                .ipAddress(ip)
                .datacenter(datacenter)
                .region(region)
                .maxSessions(maxSessions)
                .build();

        registrationService.registerNode(request);
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
