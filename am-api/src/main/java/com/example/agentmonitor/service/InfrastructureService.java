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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for infrastructure topology data.
 * Provides mock data for a 4-layer contact center topology:
 *   Trunks -> SBCs -> SIPs -> Media Servers
 *
 * Topology structure:
 * - 4 Trunks (2 carriers x 2 for redundancy)
 * - 8 SBCs (2 per datacenter)
 * - 4 SIPs (1 per datacenter)
 * - 120 Media servers (30 per datacenter)
 *
 * Datacenters:
 * - dc1 (us-east): carrier-a-1, carrier-b-1, sbc-1, sbc-2, sip-1, media-1 to media-30
 * - dc2 (us-east): carrier-a-2, carrier-b-2, sbc-3, sbc-4, sip-2, media-31 to media-60
 * - dc3 (us-west): carrier-a-3, carrier-b-3, sbc-5, sbc-6, sip-3, media-61 to media-90
 * - dc4 (us-west): carrier-a-4, carrier-b-4, sbc-7, sbc-8, sip-4, media-91 to media-120
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InfrastructureService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Random random = new Random();

    private final AtomicReference<InfrastructureTopology> currentTopology = new AtomicReference<>();

    // Trend history buffer: node ID -> circular buffer of data points
    // Stores last 60 data points (5 minutes at 5-second intervals)
    private static final int TREND_HISTORY_SIZE = 60;
    private final Map<String, LinkedList<TrendDataPoint>> trendHistoryBuffer = new ConcurrentHashMap<>();

    // Datacenter configuration
    private static final String[] DATACENTERS = {"dc1", "dc2", "dc3", "dc4"};
    private static final String[] REGIONS = {"us-east", "us-east", "us-west", "us-west"};
    private static final String[] CARRIERS = {"Carrier A", "Carrier B"};

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
     * Simulate session count and metrics changes every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void simulateSessionChanges() {
        InfrastructureTopology topology = currentTopology.get();
        if (topology == null) return;

        Instant now = Instant.now();
        List<InfrastructureNode> updatedNodes = new ArrayList<>();

        for (InfrastructureNode node : topology.getNodes()) {
            // Update trend history
            LinkedList<TrendDataPoint> history = trendHistoryBuffer.computeIfAbsent(
                node.getId(), k -> new LinkedList<>()
            );

            // Simulate metrics changes
            NodeMetrics updatedMetrics = simulateMetrics(node);

            // Randomly adjust session counts
            int delta = random.nextInt(11) - 5; // -5 to +5
            int newSessions = Math.max(0, Math.min(node.getMaxSessions(),
                    node.getActiveSessions() + delta));

            // Update session breakdown for SIP/MEDIA nodes
            SessionBreakdown sessionBreakdown = null;
            if (node.getType() == InfraServerType.SIP || node.getType() == InfraServerType.MEDIA) {
                sessionBreakdown = simulateSessionBreakdown(newSessions);
            }

            // Add new trend data point
            TrendDataPoint dataPoint = TrendDataPoint.builder()
                    .timestamp(now)
                    .activeSessions(newSessions)
                    .cpuPercent(updatedMetrics.getCpuPercent())
                    .build();

            history.addLast(dataPoint);
            while (history.size() > TREND_HISTORY_SIZE) {
                history.removeFirst();
            }

            InfrastructureNode updatedNode = InfrastructureNode.builder()
                    .id(node.getId())
                    .type(node.getType())
                    .hostname(node.getHostname())
                    .ipAddress(node.getIpAddress())
                    .startTime(node.getStartTime())
                    .activeSessions(newSessions)
                    .maxSessions(node.getMaxSessions())
                    .healthStatus(determineHealthStatus(node, updatedMetrics, newSessions))
                    .datacenter(node.getDatacenter())
                    .region(node.getRegion())
                    .metrics(updatedMetrics)
                    .sessionBreakdown(sessionBreakdown)
                    .trendHistory(new ArrayList<>(history))
                    .carrierName(node.getCarrierName())
                    .trunkGroup(node.getTrunkGroup())
                    .maintenanceMode(node.isMaintenanceMode())
                    .build();

            updatedNodes.add(updatedNode);
        }

        // Update edge metrics
        List<InfrastructureEdge> updatedEdges = new ArrayList<>();
        for (InfrastructureEdge edge : topology.getEdges()) {
            updatedEdges.add(InfrastructureEdge.builder()
                    .id(edge.getId())
                    .sourceId(edge.getSourceId())
                    .targetId(edge.getTargetId())
                    .bandwidthMbps(edge.getBandwidthMbps())
                    .latencyMs(5 + random.nextInt(20)) // 5-25ms
                    .activeFlows(random.nextInt(50))
                    .build());
        }

        InfrastructureTopology updated = InfrastructureTopology.builder()
                .nodes(updatedNodes)
                .edges(updatedEdges)
                .lastUpdated(Instant.now())
                .build();

        currentTopology.set(updated);
        broadcastTopology();
    }

    /**
     * Simulate metrics for a node.
     */
    private NodeMetrics simulateMetrics(InfrastructureNode node) {
        double utilizationFactor = node.getMaxSessions() > 0
            ? (double) node.getActiveSessions() / node.getMaxSessions()
            : 0;

        // CPU correlates with load plus some randomness
        double baseCpu = 20 + (utilizationFactor * 50);
        double cpu = Math.min(100, Math.max(0, baseCpu + (random.nextGaussian() * 5)));

        // Memory is more stable
        double memory = 40 + (utilizationFactor * 30) + (random.nextGaussian() * 3);
        memory = Math.min(100, Math.max(0, memory));

        // Latency increases with load
        int baseLatency = node.getType() == InfraServerType.TRUNK ? 10 : 5;
        int latency = baseLatency + (int)(utilizationFactor * 50) + random.nextInt(20);

        // Jitter is typically 10-20% of latency
        int jitter = Math.max(1, latency / 5 + random.nextInt(5));

        // Packet loss is rare but increases under high load
        double packetLoss = utilizationFactor > 0.8 ? random.nextDouble() * 0.5 : random.nextDouble() * 0.1;

        // Error rate correlates with load
        double errorRate = utilizationFactor * 2 * random.nextDouble();

        // MOS score (Mean Opinion Score) - higher is better (10-50 representing 1.0-5.0)
        // Decreases with latency and packet loss
        int mos = Math.max(10, Math.min(50, 45 - (latency / 10) - (int)(packetLoss * 20)));

        return NodeMetrics.builder()
                .cpuPercent(Math.round(cpu * 10) / 10.0)
                .memoryPercent(Math.round(memory * 10) / 10.0)
                .latencyMs(latency)
                .jitterMs(jitter)
                .packetLossPercent(Math.round(packetLoss * 100) / 100.0)
                .errorRate(Math.round(errorRate * 100) / 100.0)
                .mosScore(mos)
                .build();
    }

    /**
     * Simulate session breakdown for SIP/MEDIA nodes.
     */
    private SessionBreakdown simulateSessionBreakdown(int totalSessions) {
        if (totalSessions == 0) {
            return SessionBreakdown.builder()
                    .inboundSessions(0)
                    .outboundSessions(0)
                    .ivrSessions(0)
                    .queueSessions(0)
                    .agentSessions(0)
                    .onHoldSessions(0)
                    .build();
        }

        // ~70% inbound, ~30% outbound
        int inbound = (int)(totalSessions * (0.65 + random.nextDouble() * 0.1));
        int outbound = totalSessions - inbound;

        // Distribute by state: ~10% IVR, ~15% queue, ~65% agent, ~10% hold
        int ivr = Math.max(0, (int)(totalSessions * (0.08 + random.nextDouble() * 0.04)));
        int queue = Math.max(0, (int)(totalSessions * (0.12 + random.nextDouble() * 0.06)));
        int hold = Math.max(0, (int)(totalSessions * (0.08 + random.nextDouble() * 0.04)));
        int agent = Math.max(0, totalSessions - ivr - queue - hold);

        return SessionBreakdown.builder()
                .inboundSessions(inbound)
                .outboundSessions(outbound)
                .ivrSessions(ivr)
                .queueSessions(queue)
                .agentSessions(agent)
                .onHoldSessions(hold)
                .build();
    }

    /**
     * Determine health status based on metrics and capacity.
     */
    private ServerHealthStatus determineHealthStatus(InfrastructureNode node, NodeMetrics metrics, int sessions) {
        // Keep specific servers unhealthy for demo purposes
        String id = node.getId();
        if ("media-7".equals(id) || "media-45".equals(id) || "media-89".equals(id)) {
            return ServerHealthStatus.UNHEALTHY;
        }
        if ("media-12".equals(id) || "media-67".equals(id) || "media-103".equals(id)) {
            return ServerHealthStatus.DEGRADED;
        }

        // Check metrics thresholds
        if (metrics.getCpuPercent() > 90 || metrics.getMemoryPercent() > 90) {
            return ServerHealthStatus.UNHEALTHY;
        }
        if (metrics.getLatencyMs() > 200 || metrics.getPacketLossPercent() > 1.0) {
            return ServerHealthStatus.DEGRADED;
        }
        if (metrics.getCpuPercent() > 75 || metrics.getMemoryPercent() > 80) {
            return ServerHealthStatus.DEGRADED;
        }

        // Check capacity utilization
        double utilization = node.getMaxSessions() > 0
            ? (double) sessions / node.getMaxSessions()
            : 0;

        if (utilization > 0.90) {
            return ServerHealthStatus.DEGRADED;
        }

        return ServerHealthStatus.HEALTHY;
    }

    /**
     * Generate initial topology with 4-layer structure:
     * Trunks -> SBCs -> SIPs -> Media Servers
     */
    private InfrastructureTopology generateTopology() {
        List<InfrastructureNode> nodes = new ArrayList<>();
        List<InfrastructureEdge> edges = new ArrayList<>();

        Instant now = Instant.now();

        // =====================================================================
        // Generate Trunks (4 total: 2 carriers x 2 per carrier)
        // =====================================================================
        for (int carrierIdx = 0; carrierIdx < CARRIERS.length; carrierIdx++) {
            String carrier = CARRIERS[carrierIdx];
            String carrierId = carrierIdx == 0 ? "a" : "b";

            for (int i = 1; i <= 2; i++) {
                int dcIdx = carrierIdx * 2 + i - 1;
                String dc = DATACENTERS[dcIdx];
                String region = REGIONS[dcIdx];
                String trunkId = String.format("carrier-%s-%d", carrierId, i);

                int sessions = 200 + random.nextInt(200);
                NodeMetrics metrics = simulateMetrics(createDummyNode(sessions, 500));

                nodes.add(InfrastructureNode.builder()
                        .id(trunkId)
                        .type(InfraServerType.TRUNK)
                        .hostname(String.format("trunk-%s-%d.%s.example.com", carrierId, i, dc))
                        .ipAddress(String.format("203.0.%d.%d", carrierIdx + 1, i))
                        .startTime(now.minus(30 + random.nextInt(60), ChronoUnit.DAYS))
                        .activeSessions(sessions)
                        .maxSessions(500)
                        .healthStatus(ServerHealthStatus.HEALTHY)
                        .datacenter(dc)
                        .region(region)
                        .metrics(metrics)
                        .sessionBreakdown(null)
                        .trendHistory(new ArrayList<>())
                        .carrierName(carrier)
                        .trunkGroup(i == 1 ? "primary" : "backup")
                        .maintenanceMode(false)
                        .build());
            }
        }

        // =====================================================================
        // Generate SBCs (8 total: 2 per datacenter)
        // =====================================================================
        for (int i = 1; i <= 8; i++) {
            int dcIdx = (i - 1) / 2;
            String dc = DATACENTERS[dcIdx];
            String region = REGIONS[dcIdx];

            int sessions = 150 + random.nextInt(150);
            NodeMetrics metrics = simulateMetrics(createDummyNode(sessions, 400));

            nodes.add(InfrastructureNode.builder()
                    .id("sbc-" + i)
                    .type(InfraServerType.SBC)
                    .hostname(String.format("sbc-%02d.%s.example.com", i, dc))
                    .ipAddress(String.format("10.%d.0.%d", dcIdx + 1, 10 + (i % 2)))
                    .startTime(now.minus(20 + random.nextInt(40), ChronoUnit.DAYS))
                    .activeSessions(sessions)
                    .maxSessions(400)
                    .healthStatus(ServerHealthStatus.HEALTHY)
                    .datacenter(dc)
                    .region(region)
                    .metrics(metrics)
                    .sessionBreakdown(null)
                    .trendHistory(new ArrayList<>())
                    .carrierName(null)
                    .trunkGroup(null)
                    .maintenanceMode(false)
                    .build());
        }

        // =====================================================================
        // Generate SIPs (4 total: 1 per datacenter)
        // =====================================================================
        for (int i = 1; i <= 4; i++) {
            String dc = DATACENTERS[i - 1];
            String region = REGIONS[i - 1];

            int sessions = 100 + random.nextInt(300);
            NodeMetrics metrics = simulateMetrics(createDummyNode(sessions, 500));

            nodes.add(InfrastructureNode.builder()
                    .id("sip-" + i)
                    .type(InfraServerType.SIP)
                    .hostname(String.format("sip-%02d.%s.example.com", i, dc))
                    .ipAddress(String.format("10.%d.1.10", i))
                    .startTime(now.minus(i * 2L, ChronoUnit.DAYS).minus(random.nextInt(24), ChronoUnit.HOURS))
                    .activeSessions(sessions)
                    .maxSessions(500)
                    .healthStatus(ServerHealthStatus.HEALTHY)
                    .datacenter(dc)
                    .region(region)
                    .metrics(metrics)
                    .sessionBreakdown(simulateSessionBreakdown(sessions))
                    .trendHistory(new ArrayList<>())
                    .carrierName(null)
                    .trunkGroup(null)
                    .maintenanceMode(false)
                    .build());
        }

        // =====================================================================
        // Generate Media servers (120 total: 30 per datacenter)
        // =====================================================================
        for (int i = 1; i <= 120; i++) {
            int dcIdx = (i - 1) / 30;
            String dc = DATACENTERS[dcIdx];
            String region = REGIONS[dcIdx];

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

            NodeMetrics metrics = simulateMetrics(createDummyNode(sessions, 100));

            nodes.add(InfrastructureNode.builder()
                    .id("media-" + i)
                    .type(InfraServerType.MEDIA)
                    .hostname(String.format("media-%03d.%s.example.com", i, dc))
                    .ipAddress(String.format("10.%d.%d.%d", dcIdx + 1, 2 + (i % 10), 10 + ((i - 1) % 30)))
                    .startTime(now.minus(10 + random.nextInt(20), ChronoUnit.DAYS).minus(random.nextInt(24), ChronoUnit.HOURS))
                    .activeSessions(sessions)
                    .maxSessions(100)
                    .healthStatus(health)
                    .datacenter(dc)
                    .region(region)
                    .metrics(metrics)
                    .sessionBreakdown(simulateSessionBreakdown(sessions))
                    .trendHistory(new ArrayList<>())
                    .carrierName(null)
                    .trunkGroup(null)
                    .maintenanceMode(false)
                    .build());
        }

        // =====================================================================
        // Create edges: Trunks -> SBCs (within same DC)
        // =====================================================================
        // carrier-a-1 -> sbc-1, sbc-2 (dc1)
        edges.add(createEdge("carrier-a-1", "sbc-1", 1000));
        edges.add(createEdge("carrier-a-1", "sbc-2", 1000));
        // carrier-b-1 -> sbc-1, sbc-2 (dc1)
        edges.add(createEdge("carrier-b-1", "sbc-1", 1000));
        edges.add(createEdge("carrier-b-1", "sbc-2", 1000));
        // carrier-a-2 -> sbc-3, sbc-4 (dc2)
        edges.add(createEdge("carrier-a-2", "sbc-3", 1000));
        edges.add(createEdge("carrier-a-2", "sbc-4", 1000));
        // carrier-b-2 -> sbc-3, sbc-4 (dc2)
        edges.add(createEdge("carrier-b-2", "sbc-3", 1000));
        edges.add(createEdge("carrier-b-2", "sbc-4", 1000));

        // =====================================================================
        // Create edges: SBCs -> SIPs (within same DC)
        // =====================================================================
        // dc1: sbc-1, sbc-2 -> sip-1
        edges.add(createEdge("sbc-1", "sip-1", 1000));
        edges.add(createEdge("sbc-2", "sip-1", 1000));
        // dc2: sbc-3, sbc-4 -> sip-2
        edges.add(createEdge("sbc-3", "sip-2", 1000));
        edges.add(createEdge("sbc-4", "sip-2", 1000));
        // dc3: sbc-5, sbc-6 -> sip-3
        edges.add(createEdge("sbc-5", "sip-3", 1000));
        edges.add(createEdge("sbc-6", "sip-3", 1000));
        // dc4: sbc-7, sbc-8 -> sip-4
        edges.add(createEdge("sbc-7", "sip-4", 1000));
        edges.add(createEdge("sbc-8", "sip-4", 1000));

        // Add trunk connections for dc3 and dc4 (missed above pattern)
        // For dc3 and dc4, we need additional trunk nodes - but based on the plan,
        // we have 4 trunks total. Let me adjust: each carrier has 1 trunk per region,
        // so 4 trunks total connecting to the 8 SBCs across 4 DCs.
        // Actually, re-reading the plan: 4 Trunks (2 carriers x 2 for redundancy)
        // That means: carrier-a-1, carrier-a-2, carrier-b-1, carrier-b-2
        // Let's connect: carrier-a-1 & carrier-b-1 to dc1/dc2, carrier-a-2 & carrier-b-2 to dc3/dc4

        // Fix: Adjust the trunk connections for DC3 and DC4
        // Actually I had it wrong above. Let me fix the trunk generation:
        // 4 trunks: carrier-a-1 (dc1), carrier-b-1 (dc2), carrier-a-2 (dc3), carrier-b-2 (dc4)
        // But that doesn't match "2 carriers x 2 for redundancy"
        // Let me reinterpret: 2 carriers, each with 2 trunks for redundancy
        // So: carrier-a-1 (primary), carrier-a-2 (backup) from Carrier A
        //     carrier-b-1 (primary), carrier-b-2 (backup) from Carrier B

        // For simplicity, let's connect trunks to SBCs in a cross-DC manner for redundancy
        // carrier-a-1 -> sbc-1,2,3,4 (us-east DCs)
        // carrier-a-2 -> sbc-5,6,7,8 (us-west DCs)
        // carrier-b-1 -> sbc-1,2,3,4 (us-east DCs)
        // carrier-b-2 -> sbc-5,6,7,8 (us-west DCs)

        // Let me clear the previous wrong edges and recreate properly
        edges.clear();

        // Carrier A primary -> us-east SBCs
        edges.add(createEdge("carrier-a-1", "sbc-1", 1000));
        edges.add(createEdge("carrier-a-1", "sbc-2", 1000));
        edges.add(createEdge("carrier-a-1", "sbc-3", 1000));
        edges.add(createEdge("carrier-a-1", "sbc-4", 1000));

        // Carrier A backup -> us-west SBCs
        edges.add(createEdge("carrier-a-2", "sbc-5", 1000));
        edges.add(createEdge("carrier-a-2", "sbc-6", 1000));
        edges.add(createEdge("carrier-a-2", "sbc-7", 1000));
        edges.add(createEdge("carrier-a-2", "sbc-8", 1000));

        // Carrier B primary -> us-east SBCs
        edges.add(createEdge("carrier-b-1", "sbc-1", 1000));
        edges.add(createEdge("carrier-b-1", "sbc-2", 1000));
        edges.add(createEdge("carrier-b-1", "sbc-3", 1000));
        edges.add(createEdge("carrier-b-1", "sbc-4", 1000));

        // Carrier B backup -> us-west SBCs
        edges.add(createEdge("carrier-b-2", "sbc-5", 1000));
        edges.add(createEdge("carrier-b-2", "sbc-6", 1000));
        edges.add(createEdge("carrier-b-2", "sbc-7", 1000));
        edges.add(createEdge("carrier-b-2", "sbc-8", 1000));

        // SBCs -> SIPs (within same DC)
        edges.add(createEdge("sbc-1", "sip-1", 1000));
        edges.add(createEdge("sbc-2", "sip-1", 1000));
        edges.add(createEdge("sbc-3", "sip-2", 1000));
        edges.add(createEdge("sbc-4", "sip-2", 1000));
        edges.add(createEdge("sbc-5", "sip-3", 1000));
        edges.add(createEdge("sbc-6", "sip-3", 1000));
        edges.add(createEdge("sbc-7", "sip-4", 1000));
        edges.add(createEdge("sbc-8", "sip-4", 1000));

        // =====================================================================
        // Create edges: SIPs -> Media (within same DC)
        // =====================================================================
        // sip-1 (dc1) -> media-1 to media-30
        for (int m = 1; m <= 30; m++) {
            edges.add(createEdge("sip-1", "media-" + m, 100));
        }
        // sip-2 (dc2) -> media-31 to media-60
        for (int m = 31; m <= 60; m++) {
            edges.add(createEdge("sip-2", "media-" + m, 100));
        }
        // sip-3 (dc3) -> media-61 to media-90
        for (int m = 61; m <= 90; m++) {
            edges.add(createEdge("sip-3", "media-" + m, 100));
        }
        // sip-4 (dc4) -> media-91 to media-120
        for (int m = 91; m <= 120; m++) {
            edges.add(createEdge("sip-4", "media-" + m, 100));
        }

        return InfrastructureTopology.builder()
                .nodes(nodes)
                .edges(edges)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Create a dummy node for metrics simulation.
     */
    private InfrastructureNode createDummyNode(int sessions, int maxSessions) {
        return InfrastructureNode.builder()
                .activeSessions(sessions)
                .maxSessions(maxSessions)
                .type(InfraServerType.SIP)
                .build();
    }

    /**
     * Helper to create an edge with bandwidth.
     */
    private InfrastructureEdge createEdge(String sourceId, String targetId, int bandwidthMbps) {
        return InfrastructureEdge.builder()
                .id(String.format("e-%s-%s", sourceId, targetId))
                .sourceId(sourceId)
                .targetId(targetId)
                .bandwidthMbps(bandwidthMbps)
                .latencyMs(5 + random.nextInt(15))
                .activeFlows(random.nextInt(30))
                .build();
    }
}
