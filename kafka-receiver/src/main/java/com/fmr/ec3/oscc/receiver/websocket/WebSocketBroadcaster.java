package com.fmr.ec3.oscc.receiver.websocket;

import com.fmr.ec3.oscc.receiver.config.ReceiverProperties;
import com.fmr.ec3.oscc.receiver.state.RedisKeySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Broadcasts state changes to WebSocket clients.
 * Throttled: at most once per 500ms per topic.
 * All Redis reads and WebSocket sends run on a dedicated broadcast thread
 * so the Kafka consumer thread is never blocked.
 */
public class WebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final long throttleMs;
    private final boolean broadcastGridData;
    private final ConcurrentHashMap<String, Long> lastBroadcast = new ConcurrentHashMap<>();
    private final Executor broadcastExecutor;

    public WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate,
                                 StringRedisTemplate redisTemplate,
                                 ReceiverProperties props) {
        this(messagingTemplate, redisTemplate, props,
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ws-broadcast");
                t.setDaemon(true);
                return t;
            }));
    }

    // Package-private: tests pass Runnable::run for synchronous execution
    WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate,
                          StringRedisTemplate redisTemplate,
                          ReceiverProperties props,
                          Executor broadcastExecutor) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.throttleMs = props.getWebsocketThrottleMs();
        this.broadcastGridData = props.isBroadcastGridData();
        this.broadcastExecutor = broadcastExecutor;
    }

    public void broadcastAgents() {
        if (!broadcastGridData) {
            return;
        }
        submitBroadcast("/topic/agents", () -> {
            Set<String> agentIds = redisTemplate.opsForSet().members(RedisKeySchema.AGENTS_ALL_KEY);
            if (agentIds == null || agentIds.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/agents", Collections.emptyList());
                return;
            }
            List<Map<Object, Object>> agents = fetchHashesPipelined(
                agentIds, RedisKeySchema::agentKey);
            messagingTemplate.convertAndSend("/topic/agents", agents);
        });
    }

    public void broadcastCalls() {
        if (!broadcastGridData) {
            return;
        }
        submitBroadcast("/topic/calls", () -> {
            Set<String> callIds = redisTemplate.opsForSet().members(RedisKeySchema.ACTIVE_CALLS_KEY);
            if (callIds == null || callIds.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/calls", Collections.emptyList());
                return;
            }
            List<Map<Object, Object>> calls = fetchHashesPipelined(
                callIds, RedisKeySchema::callKey);
            messagingTemplate.convertAndSend("/topic/calls", calls);
        });
    }

    public void broadcastQueue() {
        if (!broadcastGridData) {
            return;
        }
        submitBroadcast("/topic/queue", () -> {
            Set<String> callIds = redisTemplate.opsForSet().members(RedisKeySchema.QUEUE_CALLS_KEY);

            List<Map<Object, Object>> calls;
            if (callIds == null || callIds.isEmpty()) {
                calls = Collections.emptyList();
            } else {
                calls = fetchHashesPipelined(callIds, RedisKeySchema::queueCallKey);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("calls", calls);
            payload.put("stats", Map.of("queuedCount", calls.size()));
            messagingTemplate.convertAndSend("/topic/queue", (Object) payload);
        });
    }

    public void broadcastSummary() {
        submitBroadcast("/topic/summary", () -> {
            List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForSet().size(RedisKeySchema.agentsByState("ONLINE"));
                    operations.opsForSet().size(RedisKeySchema.agentsByState("ON_CALL"));
                    operations.opsForSet().size(RedisKeySchema.agentsByState("AWAY"));
                    operations.opsForSet().size(RedisKeySchema.agentsByState("UNAVAILABLE"));
                    return null;
                }
            });

            if (results.size() != 4) {
                log.error("Summary pipeline returned {} results (expected 4) - skipping broadcast",
                    results.size());
                return;
            }

            int online = toInt(results.get(0));
            int onCall = toInt(results.get(1));
            int away = toInt(results.get(2));
            int unavailable = toInt(results.get(3));

            Map<String, Object> summary = Map.of(
                "online", online,
                "onCall", onCall,
                "away", away,
                "unavailable", unavailable,
                "total", online + onCall + away + unavailable
            );
            messagingTemplate.convertAndSend("/topic/summary", (Object) summary);
        });
    }

    public void broadcastInfrastructure() {
        submitBroadcast("/topic/infrastructure", () -> {
            Set<String> nodeIds = redisTemplate.opsForSet().members(RedisKeySchema.INFRA_NODES_ALL);
            if (nodeIds == null || nodeIds.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/infrastructure", Collections.emptyList());
                return;
            }
            List<String> idList = new ArrayList<>(nodeIds);

            // Pipeline: fetch info, metrics, and sessions for each node (3 HGETALL per node, 1 RT total)
            List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    for (String id : idList) {
                        operations.opsForHash().entries(RedisKeySchema.nodeInfo(id));
                        operations.opsForHash().entries(RedisKeySchema.nodeMetrics(id));
                        operations.opsForHash().entries(RedisKeySchema.nodeSessions(id));
                    }
                    return null;
                }
            });

            int expectedSize = idList.size() * 3;
            if (results.size() != expectedSize) {
                log.error("Infrastructure pipeline returned {} results (expected {}) - sending empty list",
                    results.size(), expectedSize);
                messagingTemplate.convertAndSend("/topic/infrastructure", Collections.emptyList());
                return;
            }

            List<Map<String, Object>> nodes = new ArrayList<>(idList.size());
            for (int i = 0; i < idList.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> info = (Map<Object, Object>) results.get(i * 3);
                if (info == null || info.isEmpty()) continue;

                Map<String, Object> node = new HashMap<>();
                info.forEach((k, v) -> node.put(k.toString(), v));

                @SuppressWarnings("unchecked")
                Map<Object, Object> metrics = (Map<Object, Object>) results.get(i * 3 + 1);
                if (metrics != null && !metrics.isEmpty()) {
                    Map<String, Object> metricsMap = new HashMap<>();
                    metrics.forEach((k, v) -> metricsMap.put(k.toString(), v));
                    node.put("metrics", metricsMap);
                }

                @SuppressWarnings("unchecked")
                Map<Object, Object> sessions = (Map<Object, Object>) results.get(i * 3 + 2);
                if (sessions != null && !sessions.isEmpty()) {
                    Map<String, Object> sessionsMap = new HashMap<>();
                    sessions.forEach((k, v) -> sessionsMap.put(k.toString(), v));
                    node.put("sessions", sessionsMap);
                }

                nodes.add(node);
            }
            messagingTemplate.convertAndSend("/topic/infrastructure", nodes);
        });
    }

    /**
     * Throttle check on caller thread (O(1), no I/O).
     * If it passes, submit the expensive work to the broadcast executor.
     */
    private void submitBroadcast(String topic, Runnable task) {
        if (!shouldBroadcast(topic)) return;
        broadcastExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Failed to broadcast {}", topic, e);
            }
        });
    }

    /**
     * Pipelines HGETALL for all IDs in a single Redis round-trip.
     * At 50K agents this turns 50,000 round-trips into 1.
     */
    @SuppressWarnings("unchecked")
    private List<Map<Object, Object>> fetchHashesPipelined(
            Set<String> ids, Function<String, String> keyMapper) {
        List<String> idList = new ArrayList<>(ids);

        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String id : idList) {
                    operations.opsForHash().entries(keyMapper.apply(id));
                }
                return null;
            }
        });

        List<Map<Object, Object>> out = new ArrayList<>(idList.size());
        for (Object result : results) {
            Map<Object, Object> data = (Map<Object, Object>) result;
            if (data != null && !data.isEmpty()) {
                out.add(data);
            }
        }
        return out;
    }

    private boolean shouldBroadcast(String topic) {
        long now = System.currentTimeMillis();
        Long last = lastBroadcast.get(topic);
        if (last != null && (now - last) < throttleMs) {
            return false;
        }
        lastBroadcast.put(topic, now);
        return true;
    }

    private static int toInt(Object result) {
        return result instanceof Long l ? l.intValue() : 0;
    }
}
