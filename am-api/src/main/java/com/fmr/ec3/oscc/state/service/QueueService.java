package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.exception.RedisUnavailableException;
import com.fmr.ec3.oscc.state.model.QueuedCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing calls waiting in queue.
 *
 * Redis key schema:
 * - queue:call:{callId} -> Hash containing queued call data
 * - queue:calls -> Set of queued call IDs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QueueService {

    private static final String QUEUE_CALL_PREFIX = "queue:call:";
    private static final String QUEUE_CALLS_KEY = "queue:calls";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisHealthService healthService;
    private final ObjectMapper objectMapper;

    /**
     * Get all queued calls using pipelined batch reads, sorted by priority and wait time.
     */
    @SuppressWarnings("unchecked")
    public List<QueuedCall> getQueuedCalls() {
        ensureRedisAvailable();

        Set<Object> callIds = redisTemplate.opsForSet().members(QUEUE_CALLS_KEY);
        if (callIds == null || callIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> idList = callIds.stream().map(Object::toString).toList();

        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String id : idList) {
                    operations.opsForHash().entries(QUEUE_CALL_PREFIX + id);
                }
                return null;
            }
        });

        List<QueuedCall> calls = new ArrayList<>(idList.size());
        for (Object result : results) {
            Map<Object, Object> data = (Map<Object, Object>) result;
            if (data != null && !data.isEmpty()) {
                try {
                    calls.add(objectMapper.convertValue(data, QueuedCall.class));
                } catch (Exception e) {
                    log.warn("Failed to deserialize queued call: {}", e.getMessage());
                }
            }
        }

        // Sort by priority (ascending) then by queue time (ascending = longest wait first)
        calls.sort(Comparator
                .comparingInt(QueuedCall::getPriority)
                .thenComparing(QueuedCall::getQueuedAt));

        return calls;
    }

    /**
     * Get a single queued call by ID.
     */
    public QueuedCall getQueuedCall(String callId) {
        ensureRedisAvailable();

        Map<Object, Object> data = redisTemplate.opsForHash().entries(QUEUE_CALL_PREFIX + callId);
        if (data.isEmpty()) {
            return null;
        }

        return objectMapper.convertValue(data, QueuedCall.class);
    }

    /**
     * Add a call to the queue.
     */
    public QueuedCall addToQueue(String originator, String skill, int priority) {
        return addToQueue(originator, skill, priority, Instant.now(), true);
    }

    /**
     * Add a call to the queue with a specific queue time (for test data generation).
     * @param broadcast whether to broadcast the update (set false when bulk loading)
     */
    public QueuedCall addToQueue(String originator, String skill, int priority, Instant queuedAt, boolean broadcast) {
        ensureRedisAvailable();

        String callId = UUID.randomUUID().toString();
        QueuedCall call = QueuedCall.builder()
                .id(callId)
                .originator(originator)
                .queuedAt(queuedAt)
                .skill(skill)
                .priority(priority)
                .build();

        Map<String, Object> callMap = objectMapper.convertValue(call, Map.class);
        callMap.replaceAll((k, v) -> v != null ? v.toString() : v);
        redisTemplate.opsForHash().putAll(QUEUE_CALL_PREFIX + callId, callMap);
        redisTemplate.opsForSet().add(QUEUE_CALLS_KEY, callId);

        log.info("Added call {} to queue from {} (skill: {}, priority: {})",
                callId, originator, skill, priority);

        return call;
    }

    /**
     * Remove a call from the queue (when answered or abandoned).
     */
    public QueuedCall removeFromQueue(String callId) {
        ensureRedisAvailable();

        QueuedCall call = getQueuedCall(callId);
        if (call != null) {
            redisTemplate.delete(QUEUE_CALL_PREFIX + callId);
            redisTemplate.opsForSet().remove(QUEUE_CALLS_KEY, callId);
            log.info("Removed call {} from queue", callId);
        }
        return call;
    }

    /**
     * Get the next call from the queue (highest priority, longest wait).
     * Does not remove it - use removeFromQueue after successfully connecting.
     */
    public QueuedCall peekNextCall() {
        List<QueuedCall> calls = getQueuedCalls();
        return calls.isEmpty() ? null : calls.get(0);
    }

    /**
     * Get queue statistics.
     */
    public Map<String, Object> getQueueStats() {
        List<QueuedCall> calls = getQueuedCalls();

        if (calls.isEmpty()) {
            return Map.of(
                    "queuedCount", 0,
                    "avgWaitSeconds", 0,
                    "longestWaitSeconds", 0
            );
        }

        Instant now = Instant.now();
        long totalWaitSeconds = 0;
        long longestWaitSeconds = 0;

        for (QueuedCall call : calls) {
            long waitSeconds = java.time.Duration.between(call.getQueuedAt(), now).getSeconds();
            totalWaitSeconds += waitSeconds;
            if (waitSeconds > longestWaitSeconds) {
                longestWaitSeconds = waitSeconds;
            }
        }

        return Map.of(
                "queuedCount", calls.size(),
                "avgWaitSeconds", calls.isEmpty() ? 0 : totalWaitSeconds / calls.size(),
                "longestWaitSeconds", longestWaitSeconds
        );
    }

    /**
     * Get queue count.
     */
    public int getQueueCount() {
        ensureRedisAvailable();
        Long count = redisTemplate.opsForSet().size(QUEUE_CALLS_KEY);
        return count != null ? count.intValue() : 0;
    }

    /**
     * Clear all queued calls (for testing/reset).
     */
    public void clearQueue() {
        ensureRedisAvailable();

        Set<Object> callIds = redisTemplate.opsForSet().members(QUEUE_CALLS_KEY);
        if (callIds != null) {
            for (Object id : callIds) {
                redisTemplate.delete(QUEUE_CALL_PREFIX + id.toString());
            }
        }
        redisTemplate.delete(QUEUE_CALLS_KEY);

        log.info("Cleared all queued calls");
    }

    private void ensureRedisAvailable() {
        if (!healthService.isRedisHealthy()) {
            throw new RedisUnavailableException("Redis is not available");
        }
    }
}
