package com.example.sipserver;

import com.example.osccstate.client.OsccStateClient;
import com.example.osccstate.client.OsccStateClient.NodeMetrics;
import com.example.osccstate.client.OsccStateClient.NodeType;
import com.example.osccstate.client.OsccStateClient.SessionBreakdown;
import com.example.osccstate.client.OsccStateClient.SessionInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example SIP Server integration with OSCC State API.
 *
 * <p>This example demonstrates how to integrate a SIP server with the OSCC State API
 * for health monitoring and topology visualization.
 *
 * <p>A SIP server typically:
 * <ul>
 *   <li>Registers itself on startup</li>
 *   <li>Declares connections to downstream media servers</li>
 *   <li>Sends periodic heartbeats with session counts and metrics</li>
 *   <li>Deregisters on graceful shutdown</li>
 * </ul>
 */
public class SipServerExample {

    // Simulated session counters (in real code, these would come from your SIP stack)
    private static final AtomicInteger activeSessions = new AtomicInteger(0);
    private static final AtomicInteger inboundSessions = new AtomicInteger(0);
    private static final AtomicInteger outboundSessions = new AtomicInteger(0);
    private static final AtomicInteger ivrSessions = new AtomicInteger(0);
    private static final AtomicInteger queueSessions = new AtomicInteger(0);
    private static final AtomicInteger agentSessions = new AtomicInteger(0);
    private static final AtomicInteger onHoldSessions = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        String osccStateUrl = System.getenv().getOrDefault("OSCC_STATE_URL", "http://localhost:8080");
        String nodeId = System.getenv().getOrDefault("NODE_ID", "sip-" + getHostname());
        String datacenter = System.getenv().getOrDefault("DATACENTER", "dc1");
        String region = System.getenv().getOrDefault("REGION", "us-east");
        int maxSessions = Integer.parseInt(System.getenv().getOrDefault("MAX_SESSIONS", "500"));

        // Media servers this SIP server connects to (comma-separated)
        String mediaServersEnv = System.getenv().getOrDefault("MEDIA_SERVERS", "");

        System.out.println("Starting SIP Server with OSCC State API integration");
        System.out.println("  OSCC State URL: " + osccStateUrl);
        System.out.println("  Node ID: " + nodeId);
        System.out.println("  Datacenter: " + datacenter);

        // Create and configure the OSCC State client
        OsccStateClient client = OsccStateClient.builder()
                .baseUrl(osccStateUrl)
                .nodeId(nodeId)
                .nodeType(NodeType.SIP)
                .hostname(getHostname())
                .ipAddress(getIpAddress())
                .datacenter(datacenter)
                .region(region)
                .maxSessions(maxSessions)
                .metadata(Map.of(
                        "version", "2.1.0",
                        "sipStack", "omstp",
                        "startTime", java.time.Instant.now().toString()
                ))
                .metricsSupplier(SipServerExample::collectMetrics)
                .sessionSupplier(SipServerExample::collectSessionInfo)
                .build();

        // Register shutdown hook for graceful deregistration
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down, deregistering from OSCC State API...");
            client.close();
        }));

        // Start the client (registers and begins heartbeat loop)
        client.start();

        // Register connections to downstream media servers
        List<String> connectedMediaServers = registerMediaServerConnections(client, mediaServersEnv);

        // Simulate SIP server operation with media server health monitoring
        simulateSipServerOperation(client, connectedMediaServers);
    }

    /**
     * Register connections to downstream media servers.
     *
     * <p>In a real deployment, the SIP server knows which media servers it can
     * route calls to. This topology information is sent to OSCC State API so
     * it can visualize the connections in the topology view.
     *
     * @param client The OSCC State client
     * @param mediaServersEnv Comma-separated list of media server IDs (e.g., "media-1,media-2,media-3")
     * @return List of successfully connected media server IDs
     */
    private static List<String> registerMediaServerConnections(OsccStateClient client, String mediaServersEnv) {
        List<String> connectedServers = new ArrayList<>();

        if (mediaServersEnv == null || mediaServersEnv.isBlank()) {
            System.out.println("  No media servers configured (set MEDIA_SERVERS env var)");
            return connectedServers;
        }

        List<String> mediaServerIds = new ArrayList<>();
        for (String id : mediaServersEnv.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                mediaServerIds.add(trimmed);
            }
        }

        if (mediaServerIds.isEmpty()) {
            return connectedServers;
        }

        System.out.println("  Registering connections to " + mediaServerIds.size() + " media servers...");

        try {
            // Use bulk API to add all connections at once
            var response = client.addConnectionsBulk(mediaServerIds);
            System.out.println("  Connected to media servers: added=" + response.addedCount() +
                    ", existing=" + response.existingCount() +
                    ", failed=" + response.failedCount());

            // Track successfully connected servers
            connectedServers.addAll(response.addedTargets());
            connectedServers.addAll(response.existingTargets());

            if (response.failedCount() > 0) {
                System.out.println("  Failed targets: " + response.failedTargets());
            }
        } catch (Exception e) {
            System.err.println("  Failed to register media server connections: " + e.getMessage());
        }

        return connectedServers;
    }

    /**
     * Collect current system metrics.
     * In a real implementation, you would get these from your monitoring system.
     */
    private static NodeMetrics collectMetrics() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getSystemLoadAverage();
        if (cpuLoad < 0) {
            cpuLoad = 25.0; // Fallback for systems that don't support this
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryPercent = (double) usedMemory / maxMemory * 100;

        // In a real SIP server, these would come from your RTP/SRTP stack
        Random random = new Random();
        int latencyMs = 10 + random.nextInt(15);
        int jitterMs = 1 + random.nextInt(5);
        double packetLoss = random.nextDouble() * 0.1;
        double errorRate = random.nextDouble() * 0.05;
        int mosScore = 40 + random.nextInt(8); // 4.0 - 4.8

        return new NodeMetrics(
                Math.min(cpuLoad * 10, 100), // Normalize to percentage
                memoryPercent,
                latencyMs,
                jitterMs,
                packetLoss,
                errorRate,
                mosScore
        );
    }

    /**
     * Collect current session information.
     * In a real implementation, these would come from your SIP session manager.
     */
    private static SessionInfo collectSessionInfo() {
        SessionBreakdown breakdown = new SessionBreakdown(
                inboundSessions.get(),
                outboundSessions.get(),
                ivrSessions.get(),
                queueSessions.get(),
                agentSessions.get(),
                onHoldSessions.get()
        );

        return new SessionInfo(activeSessions.get(), breakdown);
    }

    /**
     * Simulate SIP server operation with varying session counts and media server health monitoring.
     *
     * <p>This demonstrates the operational pattern of:
     * <ol>
     *   <li>Monitoring connected media servers for health</li>
     *   <li>Removing connections to unhealthy servers</li>
     *   <li>Optionally re-adding connections when servers recover</li>
     * </ol>
     *
     * @param client The OSCC State client for connection management
     * @param connectedMediaServers Mutable list of currently connected media server IDs
     */
    private static void simulateSipServerOperation(OsccStateClient client,
                                                   List<String> connectedMediaServers) throws InterruptedException {
        Random random = new Random();

        while (true) {
            // Simulate session activity
            int change = random.nextInt(21) - 10; // -10 to +10
            int newTotal = Math.max(0, Math.min(500, activeSessions.get() + change));
            activeSessions.set(newTotal);

            // Distribute sessions across categories
            inboundSessions.set((int) (newTotal * 0.6));
            outboundSessions.set((int) (newTotal * 0.4));
            ivrSessions.set((int) (newTotal * 0.1));
            queueSessions.set((int) (newTotal * 0.15));
            agentSessions.set((int) (newTotal * 0.65));
            onHoldSessions.set((int) (newTotal * 0.1));

            // Simulate periodic health check of media servers
            // In real code, this would check actual health (ping, RTP quality, etc.)
            checkMediaServerHealth(client, connectedMediaServers, random);

            Thread.sleep(2000); // Update every 2 seconds
        }
    }

    /**
     * Check health of connected media servers and remove bad ones.
     *
     * <p>In a real implementation, you would:
     * <ul>
     *   <li>Ping media servers or check RTP quality metrics</li>
     *   <li>Query OSCC State API for their health status</li>
     *   <li>Check internal call success rates to each server</li>
     * </ul>
     *
     * <p>When a media server is determined to be unhealthy:
     * <ol>
     *   <li>Stop routing new calls to it</li>
     *   <li>Remove the connection from OSCC State API (so topology reflects reality)</li>
     *   <li>Optionally drain existing calls gracefully</li>
     * </ol>
     */
    private static void checkMediaServerHealth(OsccStateClient client,
                                               List<String> connectedMediaServers,
                                               Random random) {
        if (connectedMediaServers.isEmpty()) {
            return;
        }

        // Simulate: 1% chance per check that a media server goes bad
        if (random.nextInt(100) < 1) {
            // Pick a random server to mark as "bad"
            String badServer = connectedMediaServers.get(random.nextInt(connectedMediaServers.size()));

            System.out.println("  Health check: " + badServer + " is unhealthy, removing connection...");

            try {
                // Remove the single connection to the bad server
                client.removeConnection(badServer);

                // Remove from our local tracking list
                connectedMediaServers.remove(badServer);

                System.out.println("  Removed connection to " + badServer +
                        ". Remaining connections: " + connectedMediaServers.size());

            } catch (Exception e) {
                System.err.println("  Failed to remove connection to " + badServer + ": " + e.getMessage());
            }
        }

        // Simulate: Occasionally a previously-bad server recovers and we want to reconnect
        // In real code, you'd have a list of known servers and periodically check if any
        // disconnected ones have recovered
    }

    /**
     * Example: Re-add a connection to a recovered media server.
     *
     * <p>Call this when a previously-unhealthy media server has recovered
     * and you want to start routing calls to it again.
     *
     * @param client The OSCC State client
     * @param connectedMediaServers List to track connected servers
     * @param recoveredServerId The ID of the recovered media server
     */
    private static void reconnectToRecoveredServer(OsccStateClient client,
                                                   List<String> connectedMediaServers,
                                                   String recoveredServerId) {
        System.out.println("  Media server " + recoveredServerId + " has recovered, reconnecting...");

        try {
            // Add the single connection back
            var response = client.addConnection(recoveredServerId);

            if (response.created()) {
                connectedMediaServers.add(recoveredServerId);
                System.out.println("  Reconnected to " + recoveredServerId);
            } else {
                System.out.println("  Connection to " + recoveredServerId + " already existed");
            }
        } catch (Exception e) {
            System.err.println("  Failed to reconnect to " + recoveredServerId + ": " + e.getMessage());
        }
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    private static String getIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
