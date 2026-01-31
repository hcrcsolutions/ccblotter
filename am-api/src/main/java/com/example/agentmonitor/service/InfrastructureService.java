package com.example.agentmonitor.service;

import com.example.agentmonitor.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for infrastructure topology data.
 * Provides mock data for 4 SIP servers and 120 Media servers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InfrastructureService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    private final AtomicReference<InfrastructureTopology> currentTopology = new AtomicReference<>();

    @PostConstruct
    public void init() {
        currentTopology.set(generateTopology());
        log.info("Infrastructure topology initialized with {} nodes",
                currentTopology.get().getNodes().size());
    }

    /**
     * Get current infrastructure topology.
     */
    public InfrastructureTopology getTopology() {
        return currentTopology.get();
    }

    /**
     * Broadcast infrastructure updates via WebSocket.
     */
    public void broadcastTopology() {
        InfrastructureTopology topology = currentTopology.get();
        messagingTemplate.convertAndSend("/topic/infrastructure", topology);
    }

    /**
     * Simulate session count changes every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void simulateSessionChanges() {
        InfrastructureTopology topology = currentTopology.get();
        if (topology == null) return;

        List<InfrastructureNode> updatedNodes = new ArrayList<>();

        for (InfrastructureNode node : topology.getNodes()) {
            // Randomly adjust session counts
            int delta = random.nextInt(11) - 5; // -5 to +5
            int newSessions = Math.max(0, Math.min(node.getMaxSessions(),
                    node.getActiveSessions() + delta));

            InfrastructureNode updatedNode = InfrastructureNode.builder()
                    .id(node.getId())
                    .type(node.getType())
                    .hostname(node.getHostname())
                    .ipAddress(node.getIpAddress())
                    .startTime(node.getStartTime())
                    .activeSessions(newSessions)
                    .maxSessions(node.getMaxSessions())
                    .healthStatus(determineHealthStatus(newSessions, node.getMaxSessions(), node.getId()))
                    .build();

            updatedNodes.add(updatedNode);
        }

        InfrastructureTopology updated = InfrastructureTopology.builder()
                .nodes(updatedNodes)
                .edges(topology.getEdges())
                .lastUpdated(Instant.now())
                .build();

        currentTopology.set(updated);
        broadcastTopology();
    }

    /**
     * Determine health status based on capacity and some randomness.
     */
    private ServerHealthStatus determineHealthStatus(int sessions, int maxSessions, String nodeId) {
        // Keep specific servers unhealthy for demo purposes
        if ("media-7".equals(nodeId) || "media-45".equals(nodeId)) {
            return ServerHealthStatus.UNHEALTHY;
        }

        double utilization = (double) sessions / maxSessions;

        if (utilization > 0.85) {
            return ServerHealthStatus.DEGRADED;
        }
        return ServerHealthStatus.HEALTHY;
    }

    /**
     * Generate initial topology with 4 SIP servers and 120 Media servers.
     * Each SIP server connects to 30 Media servers.
     */
    private InfrastructureTopology generateTopology() {
        List<InfrastructureNode> nodes = new ArrayList<>();
        List<InfrastructureEdge> edges = new ArrayList<>();

        Instant now = Instant.now();
        int sipCount = 4;
        int mediaPerSip = 30;
        String[] datacenters = {"dc1", "dc2", "dc3", "dc4"};

        // Generate SIP servers
        for (int i = 1; i <= sipCount; i++) {
            String dc = datacenters[i - 1];
            nodes.add(InfrastructureNode.builder()
                    .id("sip-" + i)
                    .type(InfraServerType.SIP)
                    .hostname(String.format("sip-%02d.%s.example.com", i, dc))
                    .ipAddress(String.format("10.%d.1.10", i))
                    .startTime(now.minus(i * 2L, ChronoUnit.DAYS).minus(random.nextInt(24), ChronoUnit.HOURS))
                    .activeSessions(100 + random.nextInt(300))
                    .maxSessions(500)
                    .healthStatus(ServerHealthStatus.HEALTHY)
                    .build());
        }

        // Generate Media servers (30 per SIP server)
        int mediaIndex = 1;
        for (int sipIdx = 1; sipIdx <= sipCount; sipIdx++) {
            String dc = datacenters[sipIdx - 1];
            String sipId = "sip-" + sipIdx;

            for (int m = 0; m < mediaPerSip; m++) {
                String mediaId = "media-" + mediaIndex;
                int sessions = random.nextInt(80);

                // A few servers are degraded or unhealthy for demo
                ServerHealthStatus health = ServerHealthStatus.HEALTHY;
                if (mediaIndex == 7 || mediaIndex == 45) {
                    health = ServerHealthStatus.UNHEALTHY;
                    sessions = 0;
                } else if (sessions > 70) {
                    health = ServerHealthStatus.DEGRADED;
                }

                nodes.add(InfrastructureNode.builder()
                        .id(mediaId)
                        .type(InfraServerType.MEDIA)
                        .hostname(String.format("media-%03d.%s.example.com", mediaIndex, dc))
                        .ipAddress(String.format("10.%d.2.%d", sipIdx, 10 + (m % 240)))
                        .startTime(now.minus(10 + random.nextInt(20), ChronoUnit.DAYS).minus(random.nextInt(24), ChronoUnit.HOURS))
                        .activeSessions(sessions)
                        .maxSessions(100)
                        .healthStatus(health)
                        .build());

                // Create edge from SIP to Media
                edges.add(InfrastructureEdge.builder()
                        .id(String.format("e-%s-%s", sipId, mediaId))
                        .sourceId(sipId)
                        .targetId(mediaId)
                        .build());

                mediaIndex++;
            }
        }

        return InfrastructureTopology.builder()
                .nodes(nodes)
                .edges(edges)
                .lastUpdated(Instant.now())
                .build();
    }
}
