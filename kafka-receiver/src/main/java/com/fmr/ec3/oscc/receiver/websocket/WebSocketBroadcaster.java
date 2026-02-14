package com.fmr.ec3.oscc.receiver.websocket;

import com.fmr.ec3.oscc.receiver.config.ReceiverProperties;
import com.fmr.ec3.oscc.receiver.state.RedisKeySchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Broadcasts state changes to WebSocket clients.
 * Throttled: at most once per 500ms per topic.
 */
@Component
public class WebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long throttleMs;
    private final ConcurrentHashMap<String, Long> lastBroadcast = new ConcurrentHashMap<>();

    public WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 ReceiverProperties props) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.throttleMs = props.getWebsocketThrottleMs();
    }

    public void broadcastAgents() {
        if (!shouldBroadcast("/topic/agents")) return;

        Set<String> agentIds = redisTemplate.opsForSet().members(RedisKeySchema.AGENTS_ALL_KEY);
        if (agentIds == null || agentIds.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/agents", Collections.emptyList());
            return;
        }

        List<Map<Object, Object>> agents = fetchHashesPipelined(
            agentIds, RedisKeySchema::agentKey);
        messagingTemplate.convertAndSend("/topic/agents", agents);
    }

    public void broadcastCalls() {
        if (!shouldBroadcast("/topic/calls")) return;

        Set<String> callIds = redisTemplate.opsForSet().members(RedisKeySchema.ACTIVE_CALLS_KEY);
        if (callIds == null || callIds.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/calls", Collections.emptyList());
            return;
        }

        List<Map<Object, Object>> calls = fetchHashesPipelined(
            callIds, RedisKeySchema::callKey);
        messagingTemplate.convertAndSend("/topic/calls", calls);
    }

    public void broadcastQueue() {
        if (!shouldBroadcast("/topic/queue")) return;

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

        messagingTemplate.convertAndSend("/topic/queue", payload);
    }

    public void broadcastSummary() {
        if (!shouldBroadcast("/topic/summary")) return;

        // Pipeline 4 SCARD calls into a single round-trip
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

        int online = toLong(results.get(0));
        int onCall = toLong(results.get(1));
        int away = toLong(results.get(2));
        int unavailable = toLong(results.get(3));

        Map<String, Object> summary = Map.of(
            "online", online,
            "onCall", onCall,
            "away", away,
            "unavailable", unavailable,
            "total", online + onCall + away + unavailable
        );
        messagingTemplate.convertAndSend("/topic/summary", summary);
    }

    public void broadcastInfrastructure() {
        if (!shouldBroadcast("/topic/infrastructure")) return;
        // Infrastructure broadcasts are handled by am-api which reads from Redis
        // We just signal a topology version update
        log.debug("Infrastructure state updated in Redis");
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

    private static int toLong(Object result) {
        return result instanceof Long l ? l.intValue() : 0;
    }
}
