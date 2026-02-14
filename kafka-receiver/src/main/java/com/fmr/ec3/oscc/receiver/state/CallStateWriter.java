package com.fmr.ec3.oscc.receiver.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes call state to Redis matching am-api's CallService schema exactly.
 * Keys: call:{id}, calls:active
 */
@Component
public class CallStateWriter {

    private static final Logger log = LoggerFactory.getLogger(CallStateWriter.class);

    private final StringRedisTemplate redisTemplate;

    public CallStateWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveCall(String callId, String originator, String agentId,
                          String agentName, Instant startTime, String state) {
        Map<String, String> callMap = new HashMap<>();
        callMap.put("id", callId);
        callMap.put("originator", originator);
        callMap.put("agentId", agentId);
        callMap.put("agentName", agentName);
        callMap.put("startTime", startTime.toString());
        callMap.put("state", state);

        String key = RedisKeySchema.callKey(callId);
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.opsForHash().putAll(key, callMap);
                operations.opsForSet().add(RedisKeySchema.ACTIVE_CALLS_KEY, callId);
                return operations.exec();
            }
        });

        log.debug("Saved call {} for agent {}", callId, agentId);
    }

    public void removeCall(String callId) {
        String key = RedisKeySchema.callKey(callId);
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                operations.delete(key);
                operations.opsForSet().remove(RedisKeySchema.ACTIVE_CALLS_KEY, callId);
                return operations.exec();
            }
        });

        log.debug("Removed call {}", callId);
    }

    public void updateCallState(String callId, String newState) {
        redisTemplate.opsForHash().put(RedisKeySchema.callKey(callId), "state", newState);
    }

    public boolean callExists(String callId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeySchema.callKey(callId)));
    }

    public String getCallAgentId(String callId) {
        Object agentId = redisTemplate.opsForHash().get(RedisKeySchema.callKey(callId), "agentId");
        return agentId != null ? agentId.toString() : null;
    }

    public String getCallOriginator(String callId) {
        Object originator = redisTemplate.opsForHash().get(RedisKeySchema.callKey(callId), "originator");
        return originator != null ? originator.toString() : null;
    }

    public Instant getCallStartTime(String callId) {
        Object startTime = redisTemplate.opsForHash().get(RedisKeySchema.callKey(callId), "startTime");
        return startTime != null ? Instant.parse(startTime.toString()) : null;
    }
}
