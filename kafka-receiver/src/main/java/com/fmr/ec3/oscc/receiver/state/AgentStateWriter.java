package com.fmr.ec3.oscc.receiver.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes agent state to Redis matching am-api's AgentService schema exactly.
 * Keys: agent:{id}, agents:all, agents:by-state:{state}
 */
@Component
public class AgentStateWriter {

    private static final Logger log = LoggerFactory.getLogger(AgentStateWriter.class);

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> SAVE_AGENT_SCRIPT;
    private static final DefaultRedisScript<Long> UPDATE_STATE_SCRIPT;
    private static final DefaultRedisScript<Long> REMOVE_AGENT_SCRIPT;

    static {
        // Lua script for saveAgent: atomically cleans up old state set before writing new state.
        // KEYS[1] = agent:{id}, KEYS[2] = agents:all, KEYS[3] = agents:by-state:{newState}
        // ARGV[1] = agents:by-state: prefix, ARGV[2] = agentId, ARGV[3] = name,
        // ARGV[4] = state, ARGV[5] = stateChangedAt, ARGV[6] = currentCallId (or '')
        SAVE_AGENT_SCRIPT = new DefaultRedisScript<>();
        SAVE_AGENT_SCRIPT.setScriptText(
            "local oldState = redis.call('HGET', KEYS[1], 'state') " +
            "if oldState then redis.call('SREM', ARGV[1] .. oldState, ARGV[2]) end " +
            "redis.call('HSET', KEYS[1], 'id', ARGV[2], 'name', ARGV[3], 'state', ARGV[4], 'stateChangedAt', ARGV[5]) " +
            "if ARGV[6] ~= '' then " +
            "  redis.call('HSET', KEYS[1], 'currentCallId', ARGV[6]) " +
            "else " +
            "  redis.call('HDEL', KEYS[1], 'currentCallId') " +
            "end " +
            "redis.call('SADD', KEYS[2], ARGV[2]) " +
            "redis.call('SADD', KEYS[3], ARGV[2]) " +
            "return 1"
        );
        SAVE_AGENT_SCRIPT.setResultType(Long.class);

        UPDATE_STATE_SCRIPT = new DefaultRedisScript<>();
        UPDATE_STATE_SCRIPT.setScriptText(
            "local oldState = redis.call('HGET', KEYS[1], 'state') " +
            "if not oldState then return 0 end " +
            "redis.call('SREM', ARGV[1] .. oldState, ARGV[5]) " +
            "redis.call('HSET', KEYS[1], 'state', ARGV[2], 'stateChangedAt', ARGV[3]) " +
            "if ARGV[4] ~= '' then " +
            "  redis.call('HSET', KEYS[1], 'currentCallId', ARGV[4]) " +
            "else " +
            "  redis.call('HDEL', KEYS[1], 'currentCallId') " +
            "end " +
            "redis.call('SADD', KEYS[2], ARGV[5]) " +
            "return 1"
        );
        UPDATE_STATE_SCRIPT.setResultType(Long.class);

        REMOVE_AGENT_SCRIPT = new DefaultRedisScript<>();
        REMOVE_AGENT_SCRIPT.setScriptText(
            "local state = redis.call('HGET', KEYS[1], 'state') " +
            "redis.call('DEL', KEYS[1]) " +
            "redis.call('SREM', KEYS[2], ARGV[2]) " +
            "if state then redis.call('SREM', ARGV[1] .. state, ARGV[2]) end " +
            "return 1"
        );
        REMOVE_AGENT_SCRIPT.setResultType(Long.class);
    }

    public AgentStateWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveAgent(String agentId, String name, String state, String currentCallId) {
        String key = RedisKeySchema.agentKey(agentId);
        String stateSetKey = RedisKeySchema.agentsByState(state);

        redisTemplate.execute(
            SAVE_AGENT_SCRIPT,
            List.of(key, RedisKeySchema.AGENTS_ALL_KEY, stateSetKey),
            RedisKeySchema.AGENTS_BY_STATE_PREFIX,
            agentId,
            name,
            state,
            Instant.now().toString(),
            currentCallId != null ? currentCallId : ""
        );

        log.debug("Saved agent {} state={}", agentId, state);
    }

    /**
     * @return true if the agent existed and was updated, false if agent not found
     */
    public boolean updateAgentState(String agentId, String newState, String callId) {
        String key = RedisKeySchema.agentKey(agentId);
        String newStateSetKey = RedisKeySchema.agentsByState(newState);

        Long result = redisTemplate.execute(
            UPDATE_STATE_SCRIPT,
            List.of(key, newStateSetKey),
            RedisKeySchema.AGENTS_BY_STATE_PREFIX,
            newState,
            Instant.now().toString(),
            callId != null ? callId : "",
            agentId
        );

        if (result == null || result == 0) {
            log.warn("Agent {} not found in Redis for state update", agentId);
            return false;
        }

        log.debug("Agent {} state -> {}", agentId, newState);
        return true;
    }

    public void updateLastCallInfo(String agentId, String originator,
                                    Instant startTime, Instant endTime,
                                    long durationSeconds, String reason) {
        String key = RedisKeySchema.agentKey(agentId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.warn("Agent {} no longer exists - skipping last-call metadata", agentId);
            return;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put("lastCallOriginator", originator);
        fields.put("lastCallStartTime", startTime.toString());
        fields.put("lastCallEndTime", endTime.toString());
        fields.put("lastCallDurationSeconds", String.valueOf(durationSeconds));
        fields.put("lastCallReason", reason);
        redisTemplate.opsForHash().putAll(key, fields);
    }

    /**
     * Sets a single metadata field on the agent hash.
     * Used for ancillary data like breakType and logoutReason.
     */
    public void setAgentField(String agentId, String field, String value) {
        redisTemplate.opsForHash().put(RedisKeySchema.agentKey(agentId), field, value);
    }

    public void removeAgent(String agentId) {
        String key = RedisKeySchema.agentKey(agentId);

        redisTemplate.execute(
            REMOVE_AGENT_SCRIPT,
            List.of(key, RedisKeySchema.AGENTS_ALL_KEY),
            RedisKeySchema.AGENTS_BY_STATE_PREFIX,
            agentId
        );
    }

    /**
     * Returns [state, currentCallId] in a single HMGET round-trip.
     * Both values are null if the agent does not exist.
     */
    public List<String> getAgentStateAndCallId(String agentId) {
        List<Object> values = redisTemplate.opsForHash()
            .multiGet(RedisKeySchema.agentKey(agentId), List.of("state", "currentCallId"));
        return values.stream().map(v -> v != null ? v.toString() : null).toList();
    }

    public boolean agentExists(String agentId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeySchema.agentKey(agentId)));
    }

    public String getAgentState(String agentId) {
        Object state = redisTemplate.opsForHash().get(RedisKeySchema.agentKey(agentId), "state");
        return state != null ? state.toString() : null;
    }

    public String getAgentCurrentCallId(String agentId) {
        Object callId = redisTemplate.opsForHash().get(RedisKeySchema.agentKey(agentId), "currentCallId");
        return callId != null ? callId.toString() : null;
    }

    public String getAgentName(String agentId) {
        Object name = redisTemplate.opsForHash().get(RedisKeySchema.agentKey(agentId), "name");
        return name != null ? name.toString() : null;
    }
}
