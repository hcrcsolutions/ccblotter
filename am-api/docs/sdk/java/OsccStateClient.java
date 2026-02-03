package com.example.osccstate.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client SDK for communicating with the OSCC State API.
 *
 * <p>This client handles node registration, periodic heartbeats, edge management,
 * and graceful deregistration.
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * OsccStateClient client = OsccStateClient.builder()
 *     .baseUrl("https://oscc-state.example.com:8080")
 *     .nodeId("sip-prod-01")
 *     .nodeType(NodeType.SIP)
 *     .hostname("sip-prod-01.example.com")
 *     .ipAddress("10.1.1.50")
 *     .datacenter("dc1")
 *     .region("us-east")
 *     .maxSessions(500)
 *     .metricsSupplier(() -> collectCurrentMetrics())
 *     .sessionSupplier(() -> collectSessionInfo())
 *     .build();
 *
 * // Start registration and heartbeat loop
 * client.start();
 *
 * // Add connections to downstream nodes
 * client.addConnection("media-01");
 * client.addConnectionsBulk(List.of("media-02", "media-03", "media-04"));
 *
 * // On shutdown
 * Runtime.getRuntime().addShutdownHook(new Thread(client::close));
 * }</pre>
 */
public class OsccStateClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(OsccStateClient.class.getName());

    private final String baseUrl;
    private final String nodeId;
    private final NodeType nodeType;
    private final String hostname;
    private final String ipAddress;
    private final String datacenter;
    private final String region;
    private final int maxSessions;
    private final String carrierName;
    private final String trunkGroup;
    private final Map<String, Object> metadata;

    private final Supplier<NodeMetrics> metricsSupplier;
    private final Supplier<SessionInfo> sessionSupplier;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> heartbeatTask;
    private int heartbeatIntervalSeconds = 10;

    private OsccStateClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.nodeId = builder.nodeId;
        this.nodeType = builder.nodeType;
        this.hostname = builder.hostname;
        this.ipAddress = builder.ipAddress;
        this.datacenter = builder.datacenter;
        this.region = builder.region;
        this.maxSessions = builder.maxSessions;
        this.carrierName = builder.carrierName;
        this.trunkGroup = builder.trunkGroup;
        this.metadata = builder.metadata;
        this.metricsSupplier = builder.metricsSupplier;
        this.sessionSupplier = builder.sessionSupplier;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oscc-state-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the client: register with OSCC State API and begin heartbeat loop.
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            LOG.warning("Client already started");
            return;
        }

        LOG.info("Starting OSCC State client for node: " + nodeId);

        try {
            register();
            startHeartbeatLoop();
            LOG.info("OSCC State client started successfully");
        } catch (Exception e) {
            running.set(false);
            throw new RuntimeException("Failed to start OSCC State client", e);
        }
    }

    /**
     * Stop the client: cancel heartbeats and deregister from OSCC State API.
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        LOG.info("Stopping OSCC State client for node: " + nodeId);

        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        try {
            deregister();
            LOG.info("OSCC State client stopped successfully");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to deregister node", e);
        }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Node Registration ====================

    /**
     * Register this node with OSCC State API.
     * POST /api/v1/nodes/register
     */
    public RegistrationResponse register() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                nodeId, nodeType.name(), hostname, ipAddress,
                datacenter, region, maxSessions, carrierName, trunkGroup, metadata
        );

        String json = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Registration failed: " + response.statusCode() + " - " + response.body());
        }

        RegistrationResponse regResponse = objectMapper.readValue(response.body(), RegistrationResponse.class);
        this.heartbeatIntervalSeconds = regResponse.heartbeatIntervalSeconds();

        LOG.info("Registered with OSCC State API. Heartbeat interval: " + heartbeatIntervalSeconds + "s");
        return regResponse;
    }

    /**
     * Send a heartbeat to OSCC State API.
     * POST /api/v1/nodes/{nodeId}/heartbeat
     */
    public HeartbeatResponse sendHeartbeat() throws Exception {
        SessionInfo sessionInfo = sessionSupplier != null ? sessionSupplier.get() : new SessionInfo(0, null);
        NodeMetrics metrics = metricsSupplier != null ? metricsSupplier.get() : null;

        HeartbeatRequest request = new HeartbeatRequest(
                sessionInfo.activeSessions(),
                metrics,
                sessionInfo.breakdown()
        );

        String json = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId + "/heartbeat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Heartbeat failed: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), HeartbeatResponse.class);
    }

    /**
     * Deregister this node from OSCC State API.
     * DELETE /api/v1/nodes/{nodeId}
     */
    public void deregister() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204 && response.statusCode() != 404) {
            throw new RuntimeException("Deregistration failed: " + response.statusCode() + " - " + response.body());
        }
    }

    // ==================== Edge/Connection Management ====================

    /**
     * Add a connection from this node to a target node.
     * POST /api/v1/nodes/{sourceId}/connections
     *
     * @param targetId The target node ID to connect to
     * @return Connection response
     */
    public ConnectionResponse addConnection(String targetId) throws Exception {
        return addConnection(targetId, null);
    }

    /**
     * Add a connection from this node to a target node with bandwidth specification.
     * POST /api/v1/nodes/{sourceId}/connections
     *
     * @param targetId The target node ID to connect to
     * @param bandwidthMbps Optional bandwidth in Mbps
     * @return Connection response
     */
    public ConnectionResponse addConnection(String targetId, Integer bandwidthMbps) throws Exception {
        AddConnectionRequest request = new AddConnectionRequest(targetId, bandwidthMbps);
        String json = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId + "/connections"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            throw new RuntimeException("Add connection failed: " + response.statusCode() + " - " + response.body());
        }

        LOG.info("Added connection: " + nodeId + " -> " + targetId);
        return objectMapper.readValue(response.body(), ConnectionResponse.class);
    }

    /**
     * Remove a connection from this node to a target node.
     * DELETE /api/v1/nodes/{sourceId}/connections/{targetId}
     *
     * @param targetId The target node ID to disconnect from
     */
    public void removeConnection(String targetId) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId + "/connections/" + targetId))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204) {
            throw new RuntimeException("Remove connection failed: " + response.statusCode() + " - " + response.body());
        }

        LOG.info("Removed connection: " + nodeId + " -> " + targetId);
    }

    /**
     * Add multiple connections in bulk.
     * PUT /api/v1/nodes/{sourceId}/connections/bulk
     *
     * @param targetIds List of target node IDs to connect to
     * @return Bulk connection response
     */
    public BulkConnectionResponse addConnectionsBulk(List<String> targetIds) throws Exception {
        BulkConnectionRequest request = new BulkConnectionRequest(targetIds);
        String json = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId + "/connections/bulk"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Bulk add connections failed: " + response.statusCode() + " - " + response.body());
        }

        LOG.info("Added " + targetIds.size() + " connections from " + nodeId);
        return objectMapper.readValue(response.body(), BulkConnectionResponse.class);
    }

    /**
     * Remove multiple connections in bulk.
     * DELETE /api/v1/nodes/{sourceId}/connections/bulk (with body)
     *
     * @param targetIds List of target node IDs to disconnect from
     * @return Bulk connection response
     */
    public BulkConnectionResponse removeConnectionsBulk(List<String> targetIds) throws Exception {
        BulkConnectionRequest request = new BulkConnectionRequest(targetIds);
        String json = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId + "/connections/bulk"))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Bulk remove connections failed: " + response.statusCode() + " - " + response.body());
        }

        LOG.info("Removed " + targetIds.size() + " connections from " + nodeId);
        return objectMapper.readValue(response.body(), BulkConnectionResponse.class);
    }

    /**
     * Replace all connections with a new set.
     * PUT /api/v1/nodes/{sourceId}/connections
     *
     * @param targetIds List of target node IDs (replaces all existing connections)
     * @return Bulk connection response
     */
    public BulkConnectionResponse replaceConnections(List<String> targetIds) throws Exception {
        BulkConnectionRequest request = new BulkConnectionRequest(targetIds);
        String json = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId + "/connections"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Replace connections failed: " + response.statusCode() + " - " + response.body());
        }

        LOG.info("Replaced connections for " + nodeId + " with " + targetIds.size() + " targets");
        return objectMapper.readValue(response.body(), BulkConnectionResponse.class);
    }

    /**
     * Get current connections for this node.
     * GET /api/v1/nodes/{sourceId}/connections
     *
     * @return Connection list response
     */
    public ConnectionListResponse getConnections() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/nodes/" + nodeId + "/connections"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Get connections failed: " + response.statusCode() + " - " + response.body());
        }

        return objectMapper.readValue(response.body(), ConnectionListResponse.class);
    }

    private void startHeartbeatLoop() {
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
                LOG.fine("Heartbeat sent successfully");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Heartbeat failed", e);
            }
        }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl;
        private String nodeId;
        private NodeType nodeType;
        private String hostname;
        private String ipAddress;
        private String datacenter;
        private String region;
        private int maxSessions;
        private String carrierName;
        private String trunkGroup;
        private Map<String, Object> metadata;
        private Supplier<NodeMetrics> metricsSupplier;
        private Supplier<SessionInfo> sessionSupplier;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder nodeType(NodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder datacenter(String datacenter) {
            this.datacenter = datacenter;
            return this;
        }

        public Builder region(String region) {
            this.region = region;
            return this;
        }

        public Builder maxSessions(int maxSessions) {
            this.maxSessions = maxSessions;
            return this;
        }

        public Builder carrierName(String carrierName) {
            this.carrierName = carrierName;
            return this;
        }

        public Builder trunkGroup(String trunkGroup) {
            this.trunkGroup = trunkGroup;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder metricsSupplier(Supplier<NodeMetrics> metricsSupplier) {
            this.metricsSupplier = metricsSupplier;
            return this;
        }

        public Builder sessionSupplier(Supplier<SessionInfo> sessionSupplier) {
            this.sessionSupplier = sessionSupplier;
            return this;
        }

        public OsccStateClient build() {
            validate();
            return new OsccStateClient(this);
        }

        private void validate() {
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalArgumentException("baseUrl is required");
            }
            if (nodeId == null || nodeId.isEmpty()) {
                throw new IllegalArgumentException("nodeId is required");
            }
            if (nodeType == null) {
                throw new IllegalArgumentException("nodeType is required");
            }
            if (hostname == null || hostname.isEmpty()) {
                throw new IllegalArgumentException("hostname is required");
            }
            if (ipAddress == null || ipAddress.isEmpty()) {
                throw new IllegalArgumentException("ipAddress is required");
            }
            if (datacenter == null || datacenter.isEmpty()) {
                throw new IllegalArgumentException("datacenter is required");
            }
            if (maxSessions <= 0) {
                throw new IllegalArgumentException("maxSessions must be positive");
            }
        }
    }

    // ==================== Enums ====================

    /**
     * Node types matching backend InfraServerType enum.
     */
    public enum NodeType {
        TRUNK,  // Carrier connection
        SBC,    // Session Border Controller
        SIP,    // SIP Proxy Server
        MEDIA   // Media Server
    }

    // ==================== DTOs ====================

    /**
     * Node quality metrics sent with heartbeats.
     */
    public record NodeMetrics(
            double cpuPercent,
            double memoryPercent,
            int latencyMs,
            int jitterMs,
            double packetLossPercent,
            double errorRate,        // Failed calls per minute
            int mosScore             // Mean Opinion Score (1-5 scale, x10 for precision: 10-50)
    ) {}

    /**
     * Session breakdown by direction and state.
     */
    public record SessionBreakdown(
            int inboundSessions,
            int outboundSessions,
            int ivrSessions,
            int queueSessions,
            int agentSessions,
            int onHoldSessions
    ) {}

    /**
     * Session information for heartbeat.
     */
    public record SessionInfo(
            int activeSessions,
            SessionBreakdown breakdown
    ) {}

    // --- Registration DTOs ---

    public record RegistrationRequest(
            String id,
            String type,
            String hostname,
            String ipAddress,
            String datacenter,
            String region,
            int maxSessions,
            String carrierName,
            String trunkGroup,
            Map<String, Object> metadata
    ) {}

    public record RegistrationResponse(
            String id,
            String status,
            Instant registeredAt,
            Instant reregisteredAt,
            boolean reregistered,
            int heartbeatIntervalSeconds,
            int heartbeatTimeoutSeconds,
            String message
    ) {}

    // --- Heartbeat DTOs ---

    public record HeartbeatRequest(
            int activeSessions,
            NodeMetrics metrics,
            SessionBreakdown sessionBreakdown
    ) {}

    public record HeartbeatResponse(
            String id,
            String status,
            Instant lastHeartbeat,
            Instant nextHeartbeatDue
    ) {}

    // --- Connection/Edge DTOs ---

    public record AddConnectionRequest(
            String targetId,
            Integer bandwidthMbps
    ) {}

    public record BulkConnectionRequest(
            List<String> targetIds
    ) {}

    public record ConnectionResponse(
            String id,
            String sourceId,
            String targetId,
            Integer bandwidthMbps,
            boolean created
    ) {}

    public record BulkConnectionResponse(
            String sourceId,
            int addedCount,
            int existingCount,
            int failedCount,
            List<String> addedTargets,
            List<String> existingTargets,
            List<String> failedTargets
    ) {}

    public record ConnectionListResponse(
            String sourceId,
            List<ConnectionInfo> connections,
            int total
    ) {}

    public record ConnectionInfo(
            String edgeId,
            String targetId,
            Integer bandwidthMbps
    ) {}
}
