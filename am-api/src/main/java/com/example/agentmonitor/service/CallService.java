package com.example.agentmonitor.service;

import com.example.agentmonitor.exception.RedisUnavailableException;
import com.example.agentmonitor.model.Agent;
import com.example.agentmonitor.model.AgentState;
import com.example.agentmonitor.model.Call;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing active calls in Redis.
 *
 * Redis key schema:
 * - call:{callId} -> Hash containing call data
 * - calls:active -> Set of active call IDs
 *
 * Invariants:
 * - Every call must have a valid agentId
 * - The agent must be in ON_CALL state when a call is active
 * - Starting a call atomically sets agent to ON_CALL
 * - Ending a call atomically sets agent back to ONLINE
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CallService {

    private static final String CALL_KEY_PREFIX = "call:";
    private static final String ACTIVE_CALLS_KEY = "calls:active";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final AgentService agentService;
    private final RedisHealthService healthService;
    private final ObjectMapper objectMapper;

    /**
     * Get all active calls.
     * @throws RedisUnavailableException if Redis is not available
     */
    public List<Call> getActiveCalls() {
        ensureRedisAvailable();

        List<Call> calls = new ArrayList<>();
        Set<Object> callIds = redisTemplate.opsForSet().members(ACTIVE_CALLS_KEY);

        if (callIds != null) {
            for (Object id : callIds) {
                Call call = getCall(id.toString());
                if (call != null) {
                    calls.add(call);
                }
            }
        }

        return calls;
    }

    /**
     * Get a single call by ID.
     */
    public Call getCall(String callId) {
        ensureRedisAvailable();

        Map<Object, Object> data = redisTemplate.opsForHash().entries(CALL_KEY_PREFIX + callId);
        if (data.isEmpty()) {
            return null;
        }

        return objectMapper.convertValue(data, Call.class);
    }

    /**
     * Start a new call. Atomically:
     * 1. Creates the call record
     * 2. Sets the agent to ON_CALL state
     *
     * @param originator Caller identifier
     * @param agentId Agent handling the call
     * @return The created call
     * @throws IllegalStateException if the agent is not ONLINE
     */
    public Call startCall(String originator, String agentId) {
        ensureRedisAvailable();

        Agent agent = agentService.getAgent(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        // Agent must be ONLINE to receive a call
        if (agent.getState() != AgentState.ONLINE) {
            throw new IllegalStateException(
                String.format("Agent %s cannot receive calls in state %s", agentId, agent.getState()));
        }

        // Create the call
        String callId = UUID.randomUUID().toString();
        Call call = Call.builder()
                .id(callId)
                .originator(originator)
                .agentId(agentId)
                .agentName(agent.getName())
                .startTime(Instant.now())
                .build();

        // Save call to Redis
        Map<String, Object> callMap = objectMapper.convertValue(call, Map.class);
        redisTemplate.opsForHash().putAll(CALL_KEY_PREFIX + callId, callMap);
        redisTemplate.opsForSet().add(ACTIVE_CALLS_KEY, callId);

        // Update agent state atomically (includes setting currentCallId)
        agentService.updateAgentState(agentId, AgentState.ON_CALL, callId);

        log.info("Started call {} for agent {} from {}", callId, agentId, originator);

        // Broadcast updates
        broadcastCalls();

        return call;
    }

    /**
     * End a call. Atomically:
     * 1. Saves last call info to agent
     * 2. Removes the call record
     * 3. Sets the agent back to ONLINE state
     *
     * @param callId Call to end
     * @throws IllegalStateException if call doesn't exist or agent state is inconsistent
     */
    public void endCall(String callId) {
        ensureRedisAvailable();

        Call call = getCall(callId);
        if (call == null) {
            throw new IllegalArgumentException("Call not found: " + callId);
        }

        Agent agent = agentService.getAgent(call.getAgentId());
        if (agent == null) {
            log.error("Orphaned call detected: {} has no valid agent {}", callId, call.getAgentId());
            // Clean up the orphaned call
            removeCallRecord(callId);
            broadcastCalls();
            return;
        }

        // Verify agent state consistency
        if (agent.getState() != AgentState.ON_CALL) {
            log.warn("Agent {} is not ON_CALL but has active call {}", call.getAgentId(), callId);
        }
        if (!callId.equals(agent.getCurrentCallId())) {
            log.warn("Agent {} currentCallId {} doesn't match ending call {}",
                call.getAgentId(), agent.getCurrentCallId(), callId);
        }

        // Calculate call duration and save last call info to agent
        Instant endTime = Instant.now();
        long durationSeconds = java.time.Duration.between(call.getStartTime(), endTime).getSeconds();
        agentService.updateLastCallInfo(
            call.getAgentId(),
            call.getOriginator(),
            call.getStartTime(),
            endTime,
            durationSeconds
        );

        // Remove call record
        removeCallRecord(callId);

        // Update agent state to ONLINE
        agentService.updateAgentState(call.getAgentId(), AgentState.ONLINE, null);

        log.info("Ended call {} for agent {} (duration: {}s)", callId, call.getAgentId(), durationSeconds);

        // Broadcast updates
        broadcastCalls();
    }

    private void removeCallRecord(String callId) {
        redisTemplate.delete(CALL_KEY_PREFIX + callId);
        redisTemplate.opsForSet().remove(ACTIVE_CALLS_KEY, callId);
    }

    /**
     * Broadcast current calls list to all connected clients.
     */
    public void broadcastCalls() {
        List<Call> calls = getActiveCalls();
        log.debug("Broadcasting {} active calls", calls.size());
        messagingTemplate.convertAndSend("/topic/calls", calls);
    }

    /**
     * Get the count of active calls.
     */
    public int getActiveCallCount() {
        ensureRedisAvailable();
        Long count = redisTemplate.opsForSet().size(ACTIVE_CALLS_KEY);
        return count != null ? count.intValue() : 0;
    }

    /**
     * Update a call's start time (for data generation/testing).
     * This allows backdating calls to show realistic durations.
     */
    public void updateCallStartTime(String callId, Instant newStartTime) {
        ensureRedisAvailable();

        Call call = getCall(callId);
        if (call == null) {
            throw new IllegalArgumentException("Call not found: " + callId);
        }

        call.setStartTime(newStartTime);

        Map<String, Object> callMap = objectMapper.convertValue(call, Map.class);
        redisTemplate.opsForHash().putAll(CALL_KEY_PREFIX + callId, callMap);

        log.debug("Updated call {} start time to {}", callId, newStartTime);
    }

    private void ensureRedisAvailable() {
        if (!healthService.isRedisHealthy()) {
            throw new RedisUnavailableException("Redis is not available");
        }
    }
}
