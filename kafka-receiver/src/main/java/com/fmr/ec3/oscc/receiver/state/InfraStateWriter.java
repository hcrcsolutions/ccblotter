package com.fmr.ec3.oscc.receiver.state;

import com.fmr.ec3.oscc.common.payload.infra.NodeHeartbeatPayload;
import com.fmr.ec3.oscc.common.payload.infra.NodeMetricsDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeRegisteredPayload;
import com.fmr.ec3.oscc.common.payload.infra.SessionBreakdownDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes infrastructure state to Redis matching am-api's RedisStateService schema exactly.
 * Keys: infra:node:{id}:heartbeat, infra:node:{id}:metrics,
 *        infra:node:{id}:sessions, infra:node:{id}:trends,
 *        infra:node:{id}:info, infra:nodes:all, infra:nodes:by-type:{type}
 */
@Component
public class InfraStateWriter {

    private static final Logger log = LoggerFactory.getLogger(InfraStateWriter.class);
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(90);
    private static final int MAX_TREND_ENTRIES = 60;

    private final StringRedisTemplate redisTemplate;

    public InfraStateWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void writeHeartbeat(NodeHeartbeatPayload payload) {
        String nodeId = payload.nodeId();

        // Build all data maps upfront
        NodeMetricsDto m = payload.metrics();
        Map<String, String> metrics = new HashMap<>();
        metrics.put("cpu", String.valueOf(m.cpuPercent()));
        metrics.put("memory", String.valueOf(m.memoryPercent()));
        metrics.put("latency", String.valueOf(m.latencyMs()));
        metrics.put("jitter", String.valueOf(m.jitterMs()));
        metrics.put("packetLoss", String.valueOf(m.packetLossPercent()));
        metrics.put("errorRate", String.valueOf(m.errorRate()));
        metrics.put("mos", String.valueOf(m.mosScore()));

        SessionBreakdownDto b = payload.sessionBreakdown();
        Map<String, String> sessions = new HashMap<>();
        sessions.put("active", String.valueOf(payload.activeSessions()));
        sessions.put("inbound", String.valueOf(b.inboundSessions()));
        sessions.put("outbound", String.valueOf(b.outboundSessions()));
        sessions.put("ivr", String.valueOf(b.ivrSessions()));
        sessions.put("queue", String.valueOf(b.queueSessions()));
        sessions.put("agent", String.valueOf(b.agentSessions()));
        sessions.put("onHold", String.valueOf(b.onHoldSessions()));

        String heartbeatKey = RedisKeySchema.nodeHeartbeat(nodeId);
        String metricsKey = RedisKeySchema.nodeMetrics(nodeId);
        String sessionsKey = RedisKeySchema.nodeSessions(nodeId);
        String now = Instant.now().toString();

        // Atomic transaction for heartbeat, metrics, and sessions
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForValue().set(heartbeatKey, now, HEARTBEAT_TTL);
                operations.opsForHash().putAll(metricsKey, metrics);
                operations.opsForHash().putAll(sessionsKey, sessions);
                return operations.exec();
            }
        });

        // Trends (Redis Stream) -- outside transaction as XADD+XTRIM are idempotent
        String trendsKey = RedisKeySchema.nodeTrends(nodeId);
        Map<String, String> trendData = new HashMap<>();
        trendData.put("active", String.valueOf(payload.activeSessions()));
        trendData.put("cpu", String.valueOf(m.cpuPercent()));

        MapRecord<String, String, String> record = StreamRecords.newRecord()
            .in(trendsKey)
            .ofMap(trendData);
        redisTemplate.opsForStream().add(record);
        redisTemplate.opsForStream().trim(trendsKey, MAX_TREND_ENTRIES, true);

        log.debug("Wrote heartbeat for node {}", nodeId);
    }

    public void registerNode(NodeRegisteredPayload payload) {
        String nodeId = payload.nodeId();

        Map<String, String> info = new HashMap<>();
        info.put("nodeId", nodeId);
        info.put("nodeType", payload.nodeType());
        info.put("hostname", payload.hostname());
        info.put("ipAddress", payload.ipAddress());
        info.put("datacenter", payload.datacenter());
        info.put("region", payload.region());
        info.put("maxSessions", String.valueOf(payload.maxSessions()));
        if (payload.carrierName() != null) info.put("carrierName", payload.carrierName());
        if (payload.trunkGroup() != null) info.put("trunkGroup", payload.trunkGroup());
        info.put("registeredAt", Instant.now().toString());

        // Initialize empty sessions
        Map<String, String> sessions = new HashMap<>();
        sessions.put("active", "0");
        sessions.put("inbound", "0");
        sessions.put("outbound", "0");
        sessions.put("ivr", "0");
        sessions.put("queue", "0");
        sessions.put("agent", "0");
        sessions.put("onHold", "0");

        String infoKey = RedisKeySchema.nodeInfo(nodeId);
        String heartbeatKey = RedisKeySchema.nodeHeartbeat(nodeId);
        String sessionsKey = RedisKeySchema.nodeSessions(nodeId);
        String topologyKey = RedisKeySchema.TOPOLOGY_VERSION;
        String now = Instant.now().toString();

        redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().putAll(infoKey, info);
                operations.opsForSet().add(RedisKeySchema.INFRA_NODES_ALL, nodeId);
                operations.opsForSet().add(RedisKeySchema.infraNodesByType(payload.nodeType()), nodeId);
                operations.opsForValue().set(heartbeatKey, now, HEARTBEAT_TTL);
                operations.opsForHash().putAll(sessionsKey, sessions);
                operations.opsForValue().set(topologyKey, now);
                return operations.exec();
            }
        });

        log.info("Registered node {} ({}) with direct Redis write", nodeId, payload.nodeType());
    }

    public void updateTopologyVersion() {
        redisTemplate.opsForValue().set(RedisKeySchema.TOPOLOGY_VERSION, Instant.now().toString());
    }

    public void removeNodeState(String nodeId, String nodeType) {
        String infoKey = RedisKeySchema.nodeInfo(nodeId);
        String heartbeatKey = RedisKeySchema.nodeHeartbeat(nodeId);
        String metricsKey = RedisKeySchema.nodeMetrics(nodeId);
        String sessionsKey = RedisKeySchema.nodeSessions(nodeId);
        String trendsKey = RedisKeySchema.nodeTrends(nodeId);

        redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.delete(infoKey);
                operations.delete(heartbeatKey);
                operations.delete(metricsKey);
                operations.delete(sessionsKey);
                operations.delete(trendsKey);
                operations.opsForSet().remove(RedisKeySchema.INFRA_NODES_ALL, nodeId);
                if (nodeType != null) {
                    operations.opsForSet().remove(RedisKeySchema.infraNodesByType(nodeType), nodeId);
                }
                return operations.exec();
            }
        });
    }
}
