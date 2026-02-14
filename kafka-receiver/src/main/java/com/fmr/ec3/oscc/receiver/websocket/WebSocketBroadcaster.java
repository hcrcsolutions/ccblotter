package com.fmr.ec3.oscc.receiver.websocket;

import com.fmr.ec3.oscc.receiver.config.ReceiverProperties;
import com.fmr.ec3.oscc.receiver.state.RedisKeySchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

        List<Map<Object, Object>> agents = new ArrayList<>();
        Set<String> agentIds = redisTemplate.opsForSet().members(RedisKeySchema.AGENTS_ALL_KEY);
        if (agentIds != null) {
            for (String id : agentIds) {
                Map<Object, Object> data = redisTemplate.opsForHash().entries(RedisKeySchema.agentKey(id));
                if (!data.isEmpty()) {
                    agents.add(data);
                }
            }
        }
        messagingTemplate.convertAndSend("/topic/agents", agents);
    }

    public void broadcastCalls() {
        if (!shouldBroadcast("/topic/calls")) return;

        List<Map<Object, Object>> calls = new ArrayList<>();
        Set<String> callIds = redisTemplate.opsForSet().members(RedisKeySchema.ACTIVE_CALLS_KEY);
        if (callIds != null) {
            for (String id : callIds) {
                Map<Object, Object> data = redisTemplate.opsForHash().entries(RedisKeySchema.callKey(id));
                if (!data.isEmpty()) {
                    calls.add(data);
                }
            }
        }
        messagingTemplate.convertAndSend("/topic/calls", calls);
    }

    public void broadcastQueue() {
        if (!shouldBroadcast("/topic/queue")) return;

        List<Map<Object, Object>> calls = new ArrayList<>();
        Set<String> callIds = redisTemplate.opsForSet().members(RedisKeySchema.QUEUE_CALLS_KEY);
        if (callIds != null) {
            for (String id : callIds) {
                Map<Object, Object> data = redisTemplate.opsForHash()
                    .entries(RedisKeySchema.queueCallKey(id));
                if (!data.isEmpty()) {
                    calls.add(data);
                }
            }
        }

        int queuedCount = calls.size();
        Map<String, Object> payload = new HashMap<>();
        payload.put("calls", calls);
        payload.put("stats", Map.of("queuedCount", queuedCount));

        messagingTemplate.convertAndSend("/topic/queue", payload);
    }

    public void broadcastSummary() {
        if (!shouldBroadcast("/topic/summary")) return;

        int online = getSetSize(RedisKeySchema.agentsByState("ONLINE"));
        int onCall = getSetSize(RedisKeySchema.agentsByState("ON_CALL"));
        int away = getSetSize(RedisKeySchema.agentsByState("AWAY"));
        int unavailable = getSetSize(RedisKeySchema.agentsByState("UNAVAILABLE"));

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

    private boolean shouldBroadcast(String topic) {
        long now = System.currentTimeMillis();
        Long last = lastBroadcast.get(topic);
        if (last != null && (now - last) < throttleMs) {
            return false;
        }
        lastBroadcast.put(topic, now);
        return true;
    }

    private int getSetSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size.intValue() : 0;
    }
}
