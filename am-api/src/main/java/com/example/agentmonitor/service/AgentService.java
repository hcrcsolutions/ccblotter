package com.example.agentmonitor.service;

import com.example.agentmonitor.exception.RedisUnavailableException;
import com.example.agentmonitor.model.Agent;
import com.example.agentmonitor.model.AgentState;
import com.example.agentmonitor.model.AgentSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing agent state in Redis.
 *
 * Redis key schema:
 * - agent:{agentId} -> Hash containing agent data
 * - agents:by-state:{state} -> Set of agent IDs in that state
 * - agents:all -> Set of all agent IDs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentService {

    private static final String AGENT_KEY_PREFIX = "agent:";
    private static final String AGENTS_BY_STATE_PREFIX = "agents:by-state:";
    private static final String AGENTS_ALL_KEY = "agents:all";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisHealthService healthService;
    private final ObjectMapper objectMapper;

    /**
     * Get all agents from Redis using pipelined batch reads.
     * @throws RedisUnavailableException if Redis is not available
     */
    @SuppressWarnings("unchecked")
    public List<Agent> getAllAgents() {
        ensureRedisAvailable();

        Set<Object> agentIds = redisTemplate.opsForSet().members(AGENTS_ALL_KEY);
        if (agentIds == null || agentIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> idList = agentIds.stream().map(Object::toString).toList();

        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (String id : idList) {
                    operations.opsForHash().entries(AGENT_KEY_PREFIX + id);
                }
                return null;
            }
        });

        List<Agent> agents = new ArrayList<>(idList.size());
        for (Object result : results) {
            Map<Object, Object> data = (Map<Object, Object>) result;
            if (data != null && !data.isEmpty()) {
                try {
                    agents.add(objectMapper.convertValue(data, Agent.class));
                } catch (Exception e) {
                    log.warn("Failed to deserialize agent: {}", e.getMessage());
                }
            }
        }

        return agents;
    }

    /**
     * Get a single agent by ID.
     */
    public Agent getAgent(String agentId) {
        ensureRedisAvailable();

        Map<Object, Object> data = redisTemplate.opsForHash().entries(AGENT_KEY_PREFIX + agentId);
        if (data.isEmpty()) {
            return null;
        }

        return objectMapper.convertValue(data, Agent.class);
    }

    /**
     * Get summary of agent states.
     */
    public AgentSummary getAgentSummary() {
        ensureRedisAvailable();

        int online = getCountByState(AgentState.ONLINE);
        int onCall = getCountByState(AgentState.ON_CALL);
        int away = getCountByState(AgentState.AWAY);
        int unavailable = getCountByState(AgentState.UNAVAILABLE);
        int total = online + onCall + away + unavailable;

        AgentSummary summary = AgentSummary.builder()
                .online(online)
                .onCall(onCall)
                .away(away)
                .unavailable(unavailable)
                .total(total)
                .build();

        summary.validate();
        return summary;
    }

    private int getCountByState(AgentState state) {
        Long count = redisTemplate.opsForSet().size(AGENTS_BY_STATE_PREFIX + state.name());
        return count != null ? count.intValue() : 0;
    }

    /**
     * Update agent state. Enforces state transition invariants.
     *
     * @param agentId Agent ID
     * @param newState New state
     * @param callId Call ID (required if newState is ON_CALL, null otherwise)
     * @throws IllegalStateException if invariants are violated
     */
    public void updateAgentState(String agentId, AgentState newState, String callId) {
        ensureRedisAvailable();

        Agent agent = getAgent(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        // Enforce ON_CALL invariant: must have a call ID
        if (newState == AgentState.ON_CALL && callId == null) {
            throw new IllegalStateException("Cannot set agent to ON_CALL without a call ID");
        }
        if (newState != AgentState.ON_CALL && callId != null) {
            throw new IllegalStateException("Call ID should only be set when state is ON_CALL");
        }

        AgentState oldState = agent.getState();

        // Remove from old state set
        if (oldState != null) {
            redisTemplate.opsForSet().remove(AGENTS_BY_STATE_PREFIX + oldState.name(), agentId);
        }

        // Update agent
        agent.setState(newState);
        agent.setStateChangedAt(Instant.now());
        agent.setCurrentCallId(callId);

        // Save to Redis
        Map<String, Object> agentMap = objectMapper.convertValue(agent, Map.class);
        agentMap.replaceAll((k, v) -> v != null ? v.toString() : v);
        redisTemplate.opsForHash().putAll(AGENT_KEY_PREFIX + agentId, agentMap);

        // Add to new state set
        redisTemplate.opsForSet().add(AGENTS_BY_STATE_PREFIX + newState.name(), agentId);

        log.info("Agent {} state changed: {} -> {}", agentId, oldState, newState);
    }

    /**
     * Update last call information for an agent.
     * Called when a call ends to preserve historical context.
     */
    public void updateLastCallInfo(String agentId, String originator, Instant startTime, Instant endTime, long durationSeconds) {
        ensureRedisAvailable();

        Agent agent = getAgent(agentId);
        if (agent == null) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        agent.setLastCallOriginator(originator);
        agent.setLastCallStartTime(startTime);
        agent.setLastCallEndTime(endTime);
        agent.setLastCallDurationSeconds(durationSeconds);

        Map<String, Object> agentMap = objectMapper.convertValue(agent, Map.class);
        agentMap.replaceAll((k, v) -> v != null ? v.toString() : v);
        redisTemplate.opsForHash().putAll(AGENT_KEY_PREFIX + agentId, agentMap);

        log.debug("Updated last call info for agent {}: {} ({}s)", agentId, originator, durationSeconds);
    }

    /**
     * Create or update an agent in Redis.
     */
    public void saveAgent(Agent agent) {
        ensureRedisAvailable();

        if (agent.getState() == null) {
            agent.setState(AgentState.UNAVAILABLE);
        }
        if (agent.getStateChangedAt() == null) {
            agent.setStateChangedAt(Instant.now());
        }

        // Enforce ON_CALL invariant
        if (agent.getState() == AgentState.ON_CALL && agent.getCurrentCallId() == null) {
            throw new IllegalStateException("Agent in ON_CALL state must have a currentCallId");
        }

        Map<String, Object> agentMap = objectMapper.convertValue(agent, Map.class);
        agentMap.replaceAll((k, v) -> v != null ? v.toString() : v);
        redisTemplate.opsForHash().putAll(AGENT_KEY_PREFIX + agent.getId(), agentMap);
        redisTemplate.opsForSet().add(AGENTS_ALL_KEY, agent.getId());
        redisTemplate.opsForSet().add(AGENTS_BY_STATE_PREFIX + agent.getState().name(), agent.getId());

        log.debug("Saved agent: {}", agent);
    }

    /**
     * Remove an agent from Redis.
     */
    public void removeAgent(String agentId) {
        ensureRedisAvailable();

        Agent agent = getAgent(agentId);
        if (agent != null) {
            redisTemplate.delete(AGENT_KEY_PREFIX + agentId);
            redisTemplate.opsForSet().remove(AGENTS_ALL_KEY, agentId);
            if (agent.getState() != null) {
                redisTemplate.opsForSet().remove(AGENTS_BY_STATE_PREFIX + agent.getState().name(), agentId);
            }
            log.info("Removed agent: {}", agentId);
        }
    }

    /**
     * Broadcast current agent list to all connected clients.
     */
    public void broadcastAgents() {
        List<Agent> agents = getAllAgents();
        log.debug("Broadcasting {} agents", agents.size());
        messagingTemplate.convertAndSend("/topic/agents", agents);
    }

    /**
     * Broadcast current agent summary to all connected clients.
     */
    public void broadcastSummary() {
        AgentSummary summary = getAgentSummary();
        log.debug("Broadcasting summary: {}", summary);
        messagingTemplate.convertAndSend("/topic/summary", summary);
    }

    private void ensureRedisAvailable() {
        if (!healthService.isRedisHealthy()) {
            throw new RedisUnavailableException("Redis is not available");
        }
    }
}
