package com.fmr.ec3.oscc.receiver.state;

import com.fmr.ec3.oscc.common.payload.infra.CodecUsageDto;
import com.fmr.ec3.oscc.common.payload.infra.GatewayStatusDto;
import com.fmr.ec3.oscc.common.payload.infra.NodeAlarmPayload;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes infrastructure state to Redis matching am-api's RedisStateService schema exactly.
 * Keys: infra:node:{id}:heartbeat, infra:node:{id}:metrics,
 *        infra:node:{id}:sessions, infra:node:{id}:trends,
 *        infra:node:{id}:info, infra:nodes:all, infra:nodes:by-type:{type}
 */
public class InfraStateWriter {

    private static final Logger log = LoggerFactory.getLogger(InfraStateWriter.class);
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(90);
    private static final int MAX_TREND_ENTRIES = 60;

    private static final DefaultRedisScript<Long> REMOVE_NODE_SCRIPT;

    static {
        // Lua script for removeNodeState: atomically reads nodeType, deletes all keys,
        // and cleans up both index sets — avoids TOCTOU race on nodeType lookup.
        // KEYS[1]=infoKey, KEYS[2]=heartbeatKey, KEYS[3]=metricsKey,
        // KEYS[4]=sessionsKey, KEYS[5]=trendsKey, KEYS[6]=alarmKey,
        // KEYS[7]=gatewaysKey, KEYS[8]=codecsKey, KEYS[9]=INFRA_NODES_ALL
        // ARGV[1]=nodeId, ARGV[2]=INFRA_NODES_BY_TYPE_PREFIX
        REMOVE_NODE_SCRIPT = new DefaultRedisScript<>();
        REMOVE_NODE_SCRIPT.setScriptText(
            "local nodeType = redis.call('HGET', KEYS[1], 'nodeType') " +
            "redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5], KEYS[6], KEYS[7], KEYS[8]) " +
            "redis.call('SREM', KEYS[9], ARGV[1]) " +
            "if nodeType then redis.call('SREM', ARGV[2] .. nodeType, ARGV[1]) end " +
            "return 1"
        );
        REMOVE_NODE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public InfraStateWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void writeHeartbeat(NodeHeartbeatPayload payload) {
        String nodeId = payload.nodeId();

        NodeMetricsDto m = payload.metrics();
        if (m == null) {
            log.warn("Heartbeat for node {} has null metrics - skipping", nodeId);
            return;
        }

        SessionBreakdownDto b = payload.sessionBreakdown();
        if (b == null) {
            log.warn("Heartbeat for node {} has null sessionBreakdown - skipping", nodeId);
            return;
        }

        // Build all data maps upfront
        Map<String, String> metrics = new HashMap<>();
        metrics.put("cpu", String.valueOf(m.cpuPercent()));
        metrics.put("memory", String.valueOf(m.memoryPercent()));
        metrics.put("latency", String.valueOf(m.latencyMs()));
        metrics.put("jitter", String.valueOf(m.jitterMs()));
        metrics.put("packetLoss", String.valueOf(m.packetLossPercent()));
        metrics.put("errorRate", String.valueOf(m.errorRate()));
        metrics.put("mos", String.valueOf(m.mosScore()));

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

        // Persist gateway statuses
        if (payload.gateways() != null && !payload.gateways().isEmpty()) {
            String gatewaysKey = RedisKeySchema.nodeGateways(nodeId);
            redisTemplate.delete(gatewaysKey);
            Map<String, String> gatewayMap = new HashMap<>();
            for (GatewayStatusDto gw : payload.gateways()) {
                gatewayMap.put(gw.name(), gw.state());
            }
            redisTemplate.opsForHash().putAll(gatewaysKey, gatewayMap);
        }

        // Persist codec usage
        if (payload.codecUsage() != null && !payload.codecUsage().isEmpty()) {
            String codecsKey = RedisKeySchema.nodeCodecs(nodeId);
            redisTemplate.delete(codecsKey);
            Map<String, String> codecMap = new HashMap<>();
            for (CodecUsageDto cu : payload.codecUsage()) {
                codecMap.put(cu.codec(), String.valueOf(cu.count()));
            }
            redisTemplate.opsForHash().putAll(codecsKey, codecMap);
        }

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

    public void writeAlarm(NodeAlarmPayload payload) {
        String key = RedisKeySchema.nodeAlarm(payload.nodeId());
        Map<String, String> alarm = new HashMap<>();
        alarm.put("nodeId", payload.nodeId());
        alarm.put("alarmType", payload.alarmType());
        alarm.put("severity", payload.severity());
        alarm.put("thresholdValue", String.valueOf(payload.thresholdValue()));
        alarm.put("actualValue", String.valueOf(payload.actualValue()));
        alarm.put("timestamp", Instant.now().toString());
        redisTemplate.opsForHash().putAll(key, alarm);
    }

    public void removeNodeState(String nodeId) {
        redisTemplate.execute(
            REMOVE_NODE_SCRIPT,
            List.of(
                RedisKeySchema.nodeInfo(nodeId),
                RedisKeySchema.nodeHeartbeat(nodeId),
                RedisKeySchema.nodeMetrics(nodeId),
                RedisKeySchema.nodeSessions(nodeId),
                RedisKeySchema.nodeTrends(nodeId),
                RedisKeySchema.nodeAlarm(nodeId),
                RedisKeySchema.nodeGateways(nodeId),
                RedisKeySchema.nodeCodecs(nodeId),
                RedisKeySchema.INFRA_NODES_ALL
            ),
            nodeId,
            RedisKeySchema.INFRA_NODES_BY_TYPE_PREFIX
        );
    }
}
