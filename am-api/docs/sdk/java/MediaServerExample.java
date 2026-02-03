package com.example.mediaserver;

import com.example.osccstate.client.OsccStateClient;
import com.example.osccstate.client.OsccStateClient.NodeMetrics;
import com.example.osccstate.client.OsccStateClient.NodeType;
import com.example.osccstate.client.OsccStateClient.SessionBreakdown;
import com.example.osccstate.client.OsccStateClient.SessionInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example Media Server integration with OSCC State API.
 *
 * <p>This example demonstrates how to integrate a media server with the OSCC State API
 * for health monitoring and topology visualization.
 *
 * <p>Media servers typically handle:
 * <ul>
 *   <li>RTP/SRTP audio and video streams</li>
 *   <li>Transcoding between codecs</li>
 *   <li>Conference bridges</li>
 *   <li>Call recording</li>
 *   <li>IVR prompt playback</li>
 * </ul>
 *
 * <p>Note: Media servers are typically leaf nodes in the topology. They don't declare
 * downstream connections - instead, SIP servers declare connections TO media servers.
 */
public class MediaServerExample {

    // Simulated stream/session counters (in real code, these would come from your media stack)
    private static final AtomicInteger activeStreams = new AtomicInteger(0);

    // Session breakdown - mapped to media server concepts:
    // - inboundSessions: audio-only streams
    // - outboundSessions: video streams
    // - ivrSessions: IVR playback sessions
    // - queueSessions: conference bridge participants
    // - agentSessions: transcoding sessions
    // - onHoldSessions: recording sessions
    private static final AtomicInteger audioStreams = new AtomicInteger(0);
    private static final AtomicInteger videoStreams = new AtomicInteger(0);
    private static final AtomicInteger ivrPlaybackSessions = new AtomicInteger(0);
    private static final AtomicInteger conferenceBridges = new AtomicInteger(0);
    private static final AtomicInteger transcodingSessions = new AtomicInteger(0);
    private static final AtomicInteger recordingSessions = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        String osccStateUrl = System.getenv().getOrDefault("OSCC_STATE_URL", "http://localhost:8080");
        String nodeId = System.getenv().getOrDefault("NODE_ID", "media-" + getHostname());
        String datacenter = System.getenv().getOrDefault("DATACENTER", "dc1");
        String region = System.getenv().getOrDefault("REGION", "us-east");
        int maxSessions = Integer.parseInt(System.getenv().getOrDefault("MAX_SESSIONS", "100"));

        System.out.println("Starting Media Server with OSCC State API integration");
        System.out.println("  OSCC State URL: " + osccStateUrl);
        System.out.println("  Node ID: " + nodeId);
        System.out.println("  Datacenter: " + datacenter);

        // Create and configure the OSCC State client
        OsccStateClient client = OsccStateClient.builder()
                .baseUrl(osccStateUrl)
                .nodeId(nodeId)
                .nodeType(NodeType.MEDIA)
                .hostname(getHostname())
                .ipAddress(getIpAddress())
                .datacenter(datacenter)
                .region(region)
                .maxSessions(maxSessions)
                .metadata(Map.of(
                        "version", "3.2.1",
                        "mediaEngine", "omstp-media",
                        "codecs", "G.711,G.729,OPUS,H.264",
                        "startTime", java.time.Instant.now().toString()
                ))
                .metricsSupplier(MediaServerExample::collectMetrics)
                .sessionSupplier(MediaServerExample::collectSessionInfo)
                .build();

        // Register shutdown hook for graceful deregistration
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down, deregistering from OSCC State API...");
            client.close();
        }));

        // Start the client (registers and begins heartbeat loop)
        client.start();

        // Note: Media servers are leaf nodes - they don't declare downstream connections.
        // SIP servers declare connections TO media servers.
        // If you need to query current connections:
        // var connections = client.getConnections();

        // Simulate media server operation
        simulateMediaServerOperation();
    }

    /**
     * Collect current system metrics.
     * In a real implementation, you would get these from your media processing engine.
     */
    private static NodeMetrics collectMetrics() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getSystemLoadAverage();
        if (cpuLoad < 0) {
            cpuLoad = 35.0; // Fallback for systems that don't support this
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double memoryPercent = (double) usedMemory / maxMemory * 100;

        // Media servers tend to have higher CPU due to transcoding
        // In a real server, these would come from your RTP stack and DSP monitoring
        Random random = new Random();

        // Latency/jitter are critical for media quality
        int latencyMs = 5 + random.nextInt(10);  // Media servers should have lower latency
        int jitterMs = 1 + random.nextInt(3);    // Jitter buffer compensates
        double packetLoss = random.nextDouble() * 0.05;  // Should be very low
        double errorRate = random.nextDouble() * 0.02;   // Codec errors, etc.

        // MOS score - media servers should maintain high quality
        int mosScore = 42 + random.nextInt(6); // 4.2 - 4.8

        // CPU increases with transcoding load
        double effectiveCpu = Math.min(cpuLoad * 10 + transcodingSessions.get() * 2, 100);

        return new NodeMetrics(
                effectiveCpu,
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
     * In a real implementation, these would come from your media session manager.
     *
     * <p>The SessionBreakdown fields map to media server concepts:
     * <ul>
     *   <li>inboundSessions → audio streams</li>
     *   <li>outboundSessions → video streams</li>
     *   <li>ivrSessions → IVR playback sessions</li>
     *   <li>queueSessions → conference bridge participants</li>
     *   <li>agentSessions → transcoding sessions</li>
     *   <li>onHoldSessions → recording sessions</li>
     * </ul>
     */
    private static SessionInfo collectSessionInfo() {
        // Map media server concepts to the standard breakdown fields
        SessionBreakdown breakdown = new SessionBreakdown(
                audioStreams.get(),        // inboundSessions → audio
                videoStreams.get(),        // outboundSessions → video
                ivrPlaybackSessions.get(), // ivrSessions
                conferenceBridges.get(),   // queueSessions → conferences
                transcodingSessions.get(), // agentSessions → transcoding
                recordingSessions.get()    // onHoldSessions → recording
        );

        return new SessionInfo(activeStreams.get(), breakdown);
    }

    /**
     * Simulate media server operation with varying stream counts.
     */
    private static void simulateMediaServerOperation() throws InterruptedException {
        Random random = new Random();
        int maxStreams = Integer.parseInt(System.getenv().getOrDefault("MAX_SESSIONS", "100"));

        while (true) {
            // Simulate stream activity (more stable than SIP)
            int change = random.nextInt(11) - 5; // -5 to +5
            int newTotal = Math.max(0, Math.min(maxStreams, activeStreams.get() + change));
            activeStreams.set(newTotal);

            // Distribute streams across categories
            // Media servers typically have more audio than video
            audioStreams.set((int) (newTotal * 0.75));       // 75% audio
            videoStreams.set((int) (newTotal * 0.25));       // 25% video
            transcodingSessions.set((int) (newTotal * 0.30)); // 30% need transcoding
            conferenceBridges.set((int) (newTotal * 0.10));   // 10% in conferences
            recordingSessions.set((int) (newTotal * 0.20));   // 20% being recorded
            ivrPlaybackSessions.set((int) (newTotal * 0.15)); // 15% IVR playback

            Thread.sleep(2000); // Update every 2 seconds
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
