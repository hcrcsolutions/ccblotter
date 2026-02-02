package com.example.agentmonitor.redis;

/**
 * Redis key patterns for infrastructure state.
 */
public final class RedisKeys {

    private RedisKeys() {
        // Utility class
    }

    // Key prefixes
    private static final String PREFIX = "infra:";
    private static final String NODE_PREFIX = PREFIX + "node:";

    // Node heartbeat (TTL-based liveness)
    public static String nodeHeartbeat(String nodeId) {
        return NODE_PREFIX + nodeId + ":heartbeat";
    }

    // Node session counts (Hash)
    public static String nodeSessions(String nodeId) {
        return NODE_PREFIX + nodeId + ":sessions";
    }

    // Node metrics (Hash)
    public static String nodeMetrics(String nodeId) {
        return NODE_PREFIX + nodeId + ":metrics";
    }

    // Node trend history (Stream)
    public static String nodeTrends(String nodeId) {
        return NODE_PREFIX + nodeId + ":trends";
    }

    // Topology version (for cache invalidation)
    public static final String TOPOLOGY_VERSION = PREFIX + "topology:version";

    // Watchdog lock (for distributed leader election)
    public static final String WATCHDOG_LOCK = PREFIX + "watchdog:lock";

    // Session fields
    public static final class Sessions {
        public static final String ACTIVE = "active";
        public static final String INBOUND = "inbound";
        public static final String OUTBOUND = "outbound";
        public static final String IVR = "ivr";
        public static final String QUEUE = "queue";
        public static final String AGENT = "agent";
        public static final String ON_HOLD = "onHold";
    }

    // Metrics fields
    public static final class Metrics {
        public static final String CPU = "cpu";
        public static final String MEMORY = "memory";
        public static final String LATENCY = "latency";
        public static final String JITTER = "jitter";
        public static final String PACKET_LOSS = "packetLoss";
        public static final String ERROR_RATE = "errorRate";
        public static final String MOS = "mos";
    }

    // Trend fields
    public static final class Trends {
        public static final String ACTIVE = "active";
        public static final String CPU = "cpu";
    }
}
