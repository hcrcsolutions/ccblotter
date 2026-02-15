package com.fmr.ec3.oscc.receiver.state;

/**
 * Redis key patterns matching am-api's schema exactly.
 */
public final class RedisKeySchema {

    private RedisKeySchema() {}

    // Agent keys (matching am-api AgentService)
    public static final String AGENT_KEY_PREFIX = "agent:";
    public static final String AGENTS_BY_STATE_PREFIX = "agents:by-state:";
    public static final String AGENTS_ALL_KEY = "agents:all";

    public static String agentKey(String agentId) {
        return AGENT_KEY_PREFIX + agentId;
    }

    public static String agentsByState(String state) {
        return AGENTS_BY_STATE_PREFIX + state;
    }

    // Call keys (matching am-api CallService)
    public static final String CALL_KEY_PREFIX = "call:";
    public static final String ACTIVE_CALLS_KEY = "calls:active";

    public static String callKey(String callId) {
        return CALL_KEY_PREFIX + callId;
    }

    // Queue keys (matching am-api QueueService)
    public static final String QUEUE_CALL_PREFIX = "queue:call:";
    public static final String QUEUE_CALLS_KEY = "queue:calls";

    public static String queueCallKey(String callId) {
        return QUEUE_CALL_PREFIX + callId;
    }

    // Infrastructure keys (matching am-api RedisKeys)
    private static final String INFRA_NODE_PREFIX = "infra:node:";
    public static final String INFRA_NODES_BY_TYPE_PREFIX = "infra:nodes:by-type:";

    public static String nodeInfo(String nodeId) {
        return INFRA_NODE_PREFIX + nodeId + ":info";
    }

    public static String nodeHeartbeat(String nodeId) {
        return INFRA_NODE_PREFIX + nodeId + ":heartbeat";
    }

    public static String nodeMetrics(String nodeId) {
        return INFRA_NODE_PREFIX + nodeId + ":metrics";
    }

    public static String nodeSessions(String nodeId) {
        return INFRA_NODE_PREFIX + nodeId + ":sessions";
    }

    public static String nodeTrends(String nodeId) {
        return INFRA_NODE_PREFIX + nodeId + ":trends";
    }

    public static final String INFRA_NODES_ALL = "infra:nodes:all";

    public static String infraNodesByType(String nodeType) {
        return INFRA_NODES_BY_TYPE_PREFIX + nodeType;
    }

    public static final String TOPOLOGY_VERSION = "infra:topology:version";

    // Node alarm keys
    public static String nodeAlarm(String nodeId) {
        return INFRA_NODE_PREFIX + nodeId + ":alarm";
    }

    // Watchdog leader election
    public static final String WATCHDOG_LOCK = "receiver:watchdog:lock";
}
