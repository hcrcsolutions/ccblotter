package com.fmr.ec3.oscc.receiver.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisKeySchemaTest {

    @Test
    void agentKeyMatchesAmApi() {
        assertEquals("agent:AGT-0001", RedisKeySchema.agentKey("AGT-0001"));
    }

    @Test
    void agentsByStateMatchesAmApi() {
        assertEquals("agents:by-state:ONLINE", RedisKeySchema.agentsByState("ONLINE"));
        assertEquals("agents:by-state:ON_CALL", RedisKeySchema.agentsByState("ON_CALL"));
        assertEquals("agents:by-state:AWAY", RedisKeySchema.agentsByState("AWAY"));
        assertEquals("agents:by-state:UNAVAILABLE", RedisKeySchema.agentsByState("UNAVAILABLE"));
    }

    @Test
    void agentsAllKeyMatchesAmApi() {
        assertEquals("agents:all", RedisKeySchema.AGENTS_ALL_KEY);
    }

    @Test
    void callKeyMatchesAmApi() {
        assertEquals("call:abc-123", RedisKeySchema.callKey("abc-123"));
    }

    @Test
    void activeCallsKeyMatchesAmApi() {
        assertEquals("calls:active", RedisKeySchema.ACTIVE_CALLS_KEY);
    }

    @Test
    void queueCallKeyMatchesAmApi() {
        assertEquals("queue:call:abc-123", RedisKeySchema.queueCallKey("abc-123"));
    }

    @Test
    void queueCallsKeyMatchesAmApi() {
        assertEquals("queue:calls", RedisKeySchema.QUEUE_CALLS_KEY);
    }

    @Test
    void nodeHeartbeatMatchesAmApi() {
        assertEquals("infra:node:sip-1:heartbeat", RedisKeySchema.nodeHeartbeat("sip-1"));
    }

    @Test
    void nodeMetricsMatchesAmApi() {
        assertEquals("infra:node:sip-1:metrics", RedisKeySchema.nodeMetrics("sip-1"));
    }

    @Test
    void nodeSessionsMatchesAmApi() {
        assertEquals("infra:node:sip-1:sessions", RedisKeySchema.nodeSessions("sip-1"));
    }

    @Test
    void nodeTrendsMatchesAmApi() {
        assertEquals("infra:node:sip-1:trends", RedisKeySchema.nodeTrends("sip-1"));
    }

    @Test
    void topologyVersionMatchesAmApi() {
        assertEquals("infra:topology:version", RedisKeySchema.TOPOLOGY_VERSION);
    }

    @Test
    void nodeInfoMatchesAmApi() {
        assertEquals("infra:node:sip-1:info", RedisKeySchema.nodeInfo("sip-1"));
    }

    @Test
    void infraNodesAllMatchesAmApi() {
        assertEquals("infra:nodes:all", RedisKeySchema.INFRA_NODES_ALL);
    }

    @Test
    void infraNodesByTypeMatchesAmApi() {
        assertEquals("infra:nodes:by-type:SIP", RedisKeySchema.infraNodesByType("SIP"));
        assertEquals("infra:nodes:by-type:MEDIA", RedisKeySchema.infraNodesByType("MEDIA"));
    }

    @Test
    void watchdogLockKey() {
        assertEquals("receiver:watchdog:lock", RedisKeySchema.WATCHDOG_LOCK);
    }
}
