package com.fmr.ec3.oscc.receiver.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes queue state to Redis matching am-api's QueueService schema exactly.
 * Keys: queue:call:{id}, queue:calls
 */
public class QueueStateWriter {

    private static final Logger log = LoggerFactory.getLogger(QueueStateWriter.class);

    private final StringRedisTemplate redisTemplate;

    public QueueStateWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void addToQueue(String callId, String originator, String skill,
                            int priority, Instant queuedAt) {
        Map<String, String> callMap = new HashMap<>();
        callMap.put("id", callId);
        callMap.put("originator", originator);
        callMap.put("skill", skill);
        callMap.put("priority", String.valueOf(priority));
        callMap.put("queuedAt", queuedAt.toString());

        String key = RedisKeySchema.queueCallKey(callId);
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().putAll(key, callMap);
                operations.opsForSet().add(RedisKeySchema.QUEUE_CALLS_KEY, callId);
                return operations.exec();
            }
        });

        log.debug("Added call {} to queue (skill={}, priority={})", callId, skill, priority);
    }

    public void removeFromQueue(String callId) {
        String key = RedisKeySchema.queueCallKey(callId);
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.delete(key);
                operations.opsForSet().remove(RedisKeySchema.QUEUE_CALLS_KEY, callId);
                return operations.exec();
            }
        });

        log.debug("Removed call {} from queue", callId);
    }

    public boolean queuedCallExists(String callId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeySchema.queueCallKey(callId)));
    }
}
