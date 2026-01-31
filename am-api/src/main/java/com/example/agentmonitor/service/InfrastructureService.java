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
 * Provides mock data for 4 SIP servers and 120 shared Media servers.
 *
 * Media servers can be connected to multiple SIP servers (many-to-many).
 * This simulates a real-world topology where media resources are pooled.
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
        if ("media-7".equals(nodeId) || "media-45".equals(nodeId) || "media-89".equals(nodeId)) {
            return ServerHealthStatus.UNHEALTHY;
        }
        if ("media-12".equals(nodeId) || "media-67".equals(nodeId) || "media-103".equals(nodeId)) {
            return ServerHealthStatus.DEGRADED;
        }

        double utilization = (double) sessions / maxSessions;

        if (utilization > 0.85) {
            return ServerHealthStatus.DEGRADED;
        }
        return ServerHealthStatus.HEALTHY;
    }

    /**
     * Generate initial topology with 4 SIP servers and 120 shared Media servers.
     *
     * Topology structure:
     * - 4 SIP servers (one per datacenter)
     * - 120 Media servers total (shared pool)
     * - Each SIP connects to ~40-60 media servers
     * - Many media servers are shared across multiple SIPs
     *
     * Sharing pattern:
     * - Media 1-20: Core pool shared by ALL 4 SIPs
     * - Media 21-50: Shared by SIP 1 & 2 (DC1-DC2 region)
     * - Media 51-80: Shared by SIP 3 & 4 (DC3-DC4 region)
     * - Media 81-100: Shared by SIP 1 & 3 (odd SIPs)
     * - Media 101-120: Shared by SIP 2 & 4 (even SIPs)
     */
    private InfrastructureTopology generateTopology() {
        List<InfrastructureNode> nodes = new ArrayList<>();
        List<InfrastructureEdge> edges = new ArrayList<>();

        Instant now = Instant.now();
        String[] datacenters = {"dc1", "dc2", "dc3", "dc4"};

        // =====================================================================
        // Generate 4 SIP servers
        // =====================================================================
        for (int i = 1; i <= 4; i++) {
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

        // =====================================================================
        // Generate 120 Media servers (shared pool)
        // =====================================================================
        for (int i = 1; i <= 120; i++) {
            String dc = datacenters[(i - 1) % 4];
            int sessions = random.nextInt(80);

            ServerHealthStatus health = ServerHealthStatus.HEALTHY;
            // A few servers unhealthy or degraded for demo
            if (i == 7 || i == 45 || i == 89) {
                health = ServerHealthStatus.UNHEALTHY;
                sessions = 0;
            } else if (i == 12 || i == 67 || i == 103) {
                health = ServerHealthStatus.DEGRADED;
                sessions = 85;
            } else if (sessions > 70) {
                health = ServerHealthStatus.DEGRADED;
            }

            nodes.add(InfrastructureNode.builder()
                    .id("media-" + i)
                    .type(InfraServerType.MEDIA)
                    .hostname(String.format("media-%03d.%s.example.com", i, dc))
                    .ipAddress(String.format("10.0.%d.%d", 2 + (i / 250), 10 + (i % 240)))
                    .startTime(now.minus(10 + random.nextInt(20), ChronoUnit.DAYS).minus(random.nextInt(24), ChronoUnit.HOURS))
                    .activeSessions(sessions)
                    .maxSessions(100)
                    .healthStatus(health)
                    .build());
        }

        // =====================================================================
        // Create edges (many-to-many SIP ↔ Media relationships)
        // =====================================================================

        // Core pool: Media 1-20 shared by ALL 4 SIPs
        for (int sipIdx = 1; sipIdx <= 4; sipIdx++) {
            for (int mediaIdx = 1; mediaIdx <= 20; mediaIdx++) {
                edges.add(createEdge(sipIdx, mediaIdx));
            }
        }

        // DC1-DC2 region: Media 21-50 shared by SIP 1 & 2
        for (int sipIdx = 1; sipIdx <= 2; sipIdx++) {
            for (int mediaIdx = 21; mediaIdx <= 50; mediaIdx++) {
                edges.add(createEdge(sipIdx, mediaIdx));
            }
        }

        // DC3-DC4 region: Media 51-80 shared by SIP 3 & 4
        for (int sipIdx = 3; sipIdx <= 4; sipIdx++) {
            for (int mediaIdx = 51; mediaIdx <= 80; mediaIdx++) {
                edges.add(createEdge(sipIdx, mediaIdx));
            }
        }

        // Odd SIPs pool: Media 81-100 shared by SIP 1 & 3
        for (int mediaIdx = 81; mediaIdx <= 100; mediaIdx++) {
            edges.add(createEdge(1, mediaIdx));
            edges.add(createEdge(3, mediaIdx));
        }

        // Even SIPs pool: Media 101-120 shared by SIP 2 & 4
        for (int mediaIdx = 101; mediaIdx <= 120; mediaIdx++) {
            edges.add(createEdge(2, mediaIdx));
            edges.add(createEdge(4, mediaIdx));
        }

        return InfrastructureTopology.builder()
                .nodes(nodes)
                .edges(edges)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Helper to create an edge between a SIP and Media server.
     */
    private InfrastructureEdge createEdge(int sipIdx, int mediaIdx) {
        return InfrastructureEdge.builder()
                .id(String.format("e-sip-%d-media-%d", sipIdx, mediaIdx))
                .sourceId("sip-" + sipIdx)
                .targetId("media-" + mediaIdx)
                .build();
    }
}
