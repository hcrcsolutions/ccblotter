package com.example.agentmonitor.service;

import com.example.agentmonitor.exception.RedisUnavailableException;
import com.example.agentmonitor.model.IvrSession;
import com.example.agentmonitor.model.IvrStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing IVR session state in Redis.
 *
 * Redis key schema:
 * - ivr:session:{id} -> Hash containing session data
 * - ivr:sessions:all -> Set of all session IDs
 * - ivr:session:{id}:steps -> List of step JSON objects
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IvrSessionService {

    private static final String SESSION_KEY_PREFIX = "ivr:session:";
    private static final String SESSIONS_ALL_KEY = "ivr:sessions:all";
    private static final String STEPS_KEY_SUFFIX = ":steps";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisHealthService healthService;
    private final ObjectMapper objectMapper;

    /**
     * Get all IVR sessions from Redis using pipelined batch reads.
     * @throws RedisUnavailableException if Redis is not available
     */
    @SuppressWarnings("unchecked")
    public List<IvrSession> getAllSessions() {
        ensureRedisAvailable();

        Set<Object> sessionIds = redisTemplate.opsForSet().members(SESSIONS_ALL_KEY);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> idList = sessionIds.stream().map(Object::toString).toList();

        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String id : idList) {
                    operations.opsForHash().entries(SESSION_KEY_PREFIX + id);
                }
                return null;
            }
        });

        List<IvrSession> sessions = new ArrayList<>(idList.size());
        for (Object result : results) {
            Map<Object, Object> data = (Map<Object, Object>) result;
            if (data != null && !data.isEmpty()) {
                try {
                    sessions.add(objectMapper.convertValue(data, IvrSession.class));
                } catch (Exception e) {
                    log.warn("Failed to deserialize IVR session: {}", e.getMessage());
                }
            }
        }

        return sessions;
    }

    /**
     * Get a single session by ID.
     */
    public IvrSession getSession(String sessionId) {
        ensureRedisAvailable();

        Map<Object, Object> data = redisTemplate.opsForHash().entries(SESSION_KEY_PREFIX + sessionId);
        if (data.isEmpty()) {
            return null;
        }

        return objectMapper.convertValue(data, IvrSession.class);
    }

    /**
     * Get all steps for a session, ordered by insertion time.
     */
    @SuppressWarnings("unchecked")
    public List<IvrStep> getSteps(String sessionId) {
        ensureRedisAvailable();

        String stepsKey = SESSION_KEY_PREFIX + sessionId + STEPS_KEY_SUFFIX;
        List<Object> rawSteps = redisTemplate.opsForList().range(stepsKey, 0, -1);
        if (rawSteps == null || rawSteps.isEmpty()) {
            return new ArrayList<>();
        }

        List<IvrStep> steps = new ArrayList<>(rawSteps.size());
        for (Object raw : rawSteps) {
            try {
                if (raw instanceof Map) {
                    steps.add(objectMapper.convertValue(raw, IvrStep.class));
                } else if (raw instanceof String) {
                    steps.add(objectMapper.readValue((String) raw, IvrStep.class));
                }
            } catch (Exception e) {
                log.warn("Failed to deserialize IVR step: {}", e.getMessage());
            }
        }

        return steps;
    }

    /**
     * Create or update a session in Redis.
     */
    @SuppressWarnings("unchecked")
    public void saveSession(IvrSession session) {
        ensureRedisAvailable();

        Map<String, Object> sessionMap = objectMapper.convertValue(session, Map.class);
        sessionMap.replaceAll((k, v) -> v != null ? v.toString() : v);
        redisTemplate.opsForHash().putAll(SESSION_KEY_PREFIX + session.getId(), sessionMap);
        redisTemplate.opsForSet().add(SESSIONS_ALL_KEY, session.getId());

        log.debug("Saved IVR session: {}", session.getId());
    }

    /**
     * Add a step to a session's step list.
     */
    public void addStep(IvrStep step) {
        ensureRedisAvailable();

        String stepsKey = SESSION_KEY_PREFIX + step.getSessionId() + STEPS_KEY_SUFFIX;
        try {
            String stepJson = objectMapper.writeValueAsString(step);
            redisTemplate.opsForList().rightPush(stepsKey, stepJson);
            log.debug("Added step {} to session {}", step.getId(), step.getSessionId());
        } catch (Exception e) {
            log.error("Failed to serialize IVR step: {}", e.getMessage());
        }
    }

    /**
     * Delete a session and its steps from Redis.
     */
    public void deleteSession(String sessionId) {
        ensureRedisAvailable();

        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId + STEPS_KEY_SUFFIX);
        redisTemplate.opsForSet().remove(SESSIONS_ALL_KEY, sessionId);

        log.info("Deleted IVR session: {}", sessionId);
    }

    private void ensureRedisAvailable() {
        if (!healthService.isRedisHealthy()) {
            throw new RedisUnavailableException("Redis is not available");
        }
    }
}
