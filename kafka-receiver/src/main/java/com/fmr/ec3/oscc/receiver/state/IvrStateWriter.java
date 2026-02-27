package com.fmr.ec3.oscc.receiver.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes IVR session state to Redis matching am-api's IvrSessionService schema exactly.
 * Keys: ivr:session:{id}, ivr:sessions:all, ivr:session:{id}:steps
 */
public class IvrStateWriter {

    private static final Logger log = LoggerFactory.getLogger(IvrStateWriter.class);

    private final StringRedisTemplate redisTemplate;

    public IvrStateWriter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Create a new IVR session when IVR_SESSION_STARTED is received.
     */
    public void createSession(String sessionId, String callId, String originator,
                              String flowId, Instant startTime) {
        String key = RedisKeySchema.ivrSessionKey(sessionId);

        Map<String, String> fields = new HashMap<>();
        fields.put("id", sessionId);
        fields.put("callId", callId);
        fields.put("originator", originator);
        fields.put("flowId", flowId);
        fields.put("state", "ACTIVE");
        fields.put("currentStep", "");
        fields.put("stepCount", "0");
        fields.put("startTime", startTime.toString());
        fields.put("endTime", "");
        fields.put("authenticated", "false");

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.opsForSet().add(RedisKeySchema.IVR_SESSIONS_ALL_KEY, sessionId);

        log.debug("Created IVR session {}", sessionId);
    }

    /**
     * Append a completed step and update session metadata when IVR_STEP_COMPLETED is received.
     */
    public void addStep(String sessionId, String stepId, String stepType, String stepName,
                        String prompt, String input, String outcome,
                        Instant startTime, Instant endTime, long latencyMs,
                        int retryAttempt, String error, String audioUrl) {
        String stepsKey = RedisKeySchema.ivrStepsKey(sessionId);
        String sessionKey = RedisKeySchema.ivrSessionKey(sessionId);

        // Build step JSON manually for the list (matches am-api IvrSessionService.addStep pattern)
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(escapeJson(stepId)).append("\"");
        sb.append(",\"sessionId\":\"").append(escapeJson(sessionId)).append("\"");
        sb.append(",\"stepType\":\"").append(escapeJson(stepType)).append("\"");
        sb.append(",\"stepName\":\"").append(escapeJson(stepName)).append("\"");
        appendNullableField(sb, "prompt", prompt);
        appendNullableField(sb, "input", input);
        sb.append(",\"outcome\":\"").append(escapeJson(outcome)).append("\"");
        sb.append(",\"startTime\":\"").append(startTime.toString()).append("\"");
        sb.append(",\"endTime\":\"").append(endTime.toString()).append("\"");
        sb.append(",\"latencyMs\":").append(latencyMs);
        sb.append(",\"retryAttempt\":").append(retryAttempt);
        appendNullableField(sb, "error", error);
        appendNullableField(sb, "audioUrl", audioUrl);
        sb.append("}");

        redisTemplate.opsForList().rightPush(stepsKey, sb.toString());

        // Update session metadata
        Long stepCount = redisTemplate.opsForList().size(stepsKey);
        Map<String, String> updates = new HashMap<>();
        updates.put("currentStep", stepName);
        updates.put("stepCount", String.valueOf(stepCount != null ? stepCount : 0));

        // Update state based on step type
        if ("AUTHENTICATE".equals(stepType)) {
            if ("auth-success".equals(outcome)) {
                updates.put("authenticated", "true");
                updates.put("state", "ACTIVE");
            } else if ("auth-failed".equals(outcome)) {
                updates.put("state", "AUTHENTICATING");
            } else {
                updates.put("state", "AUTHENTICATING");
            }
        } else if ("TRANSFER".equals(stepType)) {
            updates.put("state", "TRANSFERRING");
        }

        redisTemplate.opsForHash().putAll(sessionKey, updates);

        log.debug("Added step {} to IVR session {}", stepId, sessionId);
    }

    /**
     * End an IVR session when IVR_SESSION_ENDED is received.
     */
    public void endSession(String sessionId, String endState, Instant endTime,
                           int stepCount, boolean authenticated) {
        String sessionKey = RedisKeySchema.ivrSessionKey(sessionId);

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey))) {
            log.warn("IVR session {} not found for end event", sessionId);
            return;
        }

        Map<String, String> updates = new HashMap<>();
        updates.put("state", endState);
        updates.put("endTime", endTime.toString());
        updates.put("stepCount", String.valueOf(stepCount));
        updates.put("authenticated", String.valueOf(authenticated));

        redisTemplate.opsForHash().putAll(sessionKey, updates);

        log.debug("Ended IVR session {} with state {}", sessionId, endState);
    }

    /**
     * Check if a session exists.
     */
    public boolean sessionExists(String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeySchema.ivrSessionKey(sessionId)));
    }

    private void appendNullableField(StringBuilder sb, String name, String value) {
        sb.append(",\"").append(name).append("\":");
        if (value != null) {
            sb.append("\"").append(escapeJson(value)).append("\"");
        } else {
            sb.append("null");
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
