package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.model.NodeMetrics;
import com.fmr.ec3.oscc.state.model.SessionBreakdown;
import com.fmr.ec3.oscc.state.model.TrendDataPoint;
import com.fmr.ec3.oscc.state.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisStateService {

    private final StringRedisTemplate redisTemplate;

    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(90);
    private static final int MAX_TREND_ENTRIES = 60;

    /**
     * Initialize Redis state for a new node.
     */
    public void initializeNodeState(String nodeId) {
        updateHeartbeat(nodeId, Instant.now());
        setActiveSessions(nodeId, 0);
        log.debug("Initialized Redis state for node: {}", nodeId);
    }

    /**
     * Update heartbeat timestamp.
     */
    public void updateHeartbeat(String nodeId, Instant timestamp) {
        String key = RedisKeys.nodeHeartbeat(nodeId);
        redisTemplate.opsForValue().set(key, timestamp.toString(), HEARTBEAT_TTL);
    }

    /**
     * Check if a node heartbeat is still valid.
     */
    public boolean isHeartbeatValid(String nodeId) {
        String key = RedisKeys.nodeHeartbeat(nodeId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get last heartbeat timestamp.
     */
    public Optional<Instant> getLastHeartbeat(String nodeId) {
        String key = RedisKeys.nodeHeartbeat(nodeId);
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            return Optional.of(Instant.parse(value));
        }
        return Optional.empty();
    }

    /**
     * Set active sessions count.
     */
    public void setActiveSessions(String nodeId, int activeSessions) {
        String key = RedisKeys.nodeSessions(nodeId);
        redisTemplate.opsForHash().put(key, RedisKeys.Sessions.ACTIVE, String.valueOf(activeSessions));
    }

    /**
     * Set session counts with breakdown.
     */
    public void setSessionCounts(String nodeId, int activeSessions, SessionBreakdown breakdown) {
        String key = RedisKeys.nodeSessions(nodeId);
        Map<String, String> sessions = new HashMap<>();
        sessions.put(RedisKeys.Sessions.ACTIVE, String.valueOf(activeSessions));
        sessions.put(RedisKeys.Sessions.INBOUND, String.valueOf(breakdown.getInboundSessions()));
        sessions.put(RedisKeys.Sessions.OUTBOUND, String.valueOf(breakdown.getOutboundSessions()));
        sessions.put(RedisKeys.Sessions.IVR, String.valueOf(breakdown.getIvrSessions()));
        sessions.put(RedisKeys.Sessions.QUEUE, String.valueOf(breakdown.getQueueSessions()));
        sessions.put(RedisKeys.Sessions.AGENT, String.valueOf(breakdown.getAgentSessions()));
        sessions.put(RedisKeys.Sessions.ON_HOLD, String.valueOf(breakdown.getOnHoldSessions()));
        redisTemplate.opsForHash().putAll(key, sessions);
    }

    /**
     * Get active sessions count.
     */
    public Integer getActiveSessions(String nodeId) {
        String key = RedisKeys.nodeSessions(nodeId);
        Object value = redisTemplate.opsForHash().get(key, RedisKeys.Sessions.ACTIVE);
        if (value != null) {
            int parsed = parseInt(value);
            return parsed;
        }
        return null;
    }

    /**
     * Get full session breakdown.
     */
    public SessionBreakdown getSessionBreakdown(String nodeId) {
        String key = RedisKeys.nodeSessions(nodeId);
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) {
            return null;
        }

        return SessionBreakdown.builder()
                .inboundSessions(parseInt(data.get(RedisKeys.Sessions.INBOUND)))
                .outboundSessions(parseInt(data.get(RedisKeys.Sessions.OUTBOUND)))
                .ivrSessions(parseInt(data.get(RedisKeys.Sessions.IVR)))
                .queueSessions(parseInt(data.get(RedisKeys.Sessions.QUEUE)))
                .agentSessions(parseInt(data.get(RedisKeys.Sessions.AGENT)))
                .onHoldSessions(parseInt(data.get(RedisKeys.Sessions.ON_HOLD)))
                .build();
    }

    /**
     * Set node metrics.
     */
    public void setNodeMetrics(String nodeId, NodeMetrics metrics) {
        String key = RedisKeys.nodeMetrics(nodeId);
        Map<String, String> data = new HashMap<>();
        data.put(RedisKeys.Metrics.CPU, String.valueOf(metrics.getCpuPercent()));
        data.put(RedisKeys.Metrics.MEMORY, String.valueOf(metrics.getMemoryPercent()));
        data.put(RedisKeys.Metrics.LATENCY, String.valueOf(metrics.getLatencyMs()));
        data.put(RedisKeys.Metrics.JITTER, String.valueOf(metrics.getJitterMs()));
        data.put(RedisKeys.Metrics.PACKET_LOSS, String.valueOf(metrics.getPacketLossPercent()));
        data.put(RedisKeys.Metrics.ERROR_RATE, String.valueOf(metrics.getErrorRate()));
        data.put(RedisKeys.Metrics.MOS, String.valueOf(metrics.getMosScore()));
        redisTemplate.opsForHash().putAll(key, data);
    }

    /**
     * Get node metrics.
     */
    public NodeMetrics getNodeMetrics(String nodeId) {
        String key = RedisKeys.nodeMetrics(nodeId);
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) {
            return null;
        }

        return NodeMetrics.builder()
                .cpuPercent(parseDouble(data.get(RedisKeys.Metrics.CPU)))
                .memoryPercent(parseDouble(data.get(RedisKeys.Metrics.MEMORY)))
                .latencyMs(parseInt(data.get(RedisKeys.Metrics.LATENCY)))
                .jitterMs(parseInt(data.get(RedisKeys.Metrics.JITTER)))
                .packetLossPercent(parseDouble(data.get(RedisKeys.Metrics.PACKET_LOSS)))
                .errorRate(parseDouble(data.get(RedisKeys.Metrics.ERROR_RATE)))
                .mosScore(parseInt(data.get(RedisKeys.Metrics.MOS)))
                .build();
    }

    /**
     * Add a trend data point.
     */
    public void addTrendDataPoint(String nodeId, int activeSessions, double cpuPercent) {
        String key = RedisKeys.nodeTrends(nodeId);
        Map<String, String> data = new HashMap<>();
        data.put(RedisKeys.Trends.ACTIVE, String.valueOf(activeSessions));
        data.put(RedisKeys.Trends.CPU, String.valueOf(cpuPercent));

        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in(key)
                .ofMap(data);

        redisTemplate.opsForStream().add(record);

        // Trim to max entries
        redisTemplate.opsForStream().trim(key, MAX_TREND_ENTRIES, true);
    }

    /**
     * Get trend history.
     */
    public List<TrendDataPoint> getTrendHistory(String nodeId) {
        String key = RedisKeys.nodeTrends(nodeId);
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .read(org.springframework.data.redis.connection.stream.StreamOffset.fromStart(key));

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        return records.stream()
                .map(record -> {
                    Map<Object, Object> values = record.getValue();
                    return TrendDataPoint.builder()
                            .timestamp(Instant.ofEpochMilli(record.getId().getTimestamp()))
                            .activeSessions(parseInt(values.get(RedisKeys.Trends.ACTIVE)))
                            .cpuPercent(parseDouble(values.get(RedisKeys.Trends.CPU)))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get gateway statuses for a node.
     */
    public Map<String, String> getGatewayStatuses(String nodeId) {
        String key = RedisKeys.nodeGateways(nodeId);
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        data.forEach((k, v) -> result.put(k.toString(), v.toString()));
        return result;
    }

    /**
     * Get codec usage for a node.
     */
    public Map<String, Integer> getCodecUsage(String nodeId) {
        String key = RedisKeys.nodeCodecs(nodeId);
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
        if (data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<>();
        data.forEach((k, v) -> result.put(k.toString(), parseInt(v)));
        return result;
    }

    /**
     * Update topology version (for cache invalidation).
     */
    public void updateTopologyVersion() {
        redisTemplate.opsForValue().set(RedisKeys.TOPOLOGY_VERSION, Instant.now().toString());
    }

    /**
     * Get current topology version.
     */
    public String getTopologyVersion() {
        return redisTemplate.opsForValue().get(RedisKeys.TOPOLOGY_VERSION);
    }

    /**
     * Remove all Redis state for a node.
     */
    public void removeNodeState(String nodeId) {
        Set<String> keys = Set.of(
                RedisKeys.nodeHeartbeat(nodeId),
                RedisKeys.nodeSessions(nodeId),
                RedisKeys.nodeMetrics(nodeId),
                RedisKeys.nodeTrends(nodeId),
                RedisKeys.nodeGateways(nodeId),
                RedisKeys.nodeCodecs(nodeId)
        );
        redisTemplate.delete(keys);
        log.debug("Removed Redis state for node: {}", nodeId);
    }

    /**
     * Try to acquire watchdog lock.
     */
    public boolean tryAcquireWatchdogLock(String instanceId, Duration ttl) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(
                        RedisKeys.WATCHDOG_LOCK, instanceId, ttl));
    }

    private int parseInt(Object value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
