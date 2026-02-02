package com.example.agentmonitor.controller;

import com.example.agentmonitor.exception.RedisUnavailableException;
import com.example.agentmonitor.model.Agent;
import com.example.agentmonitor.model.AgentSummary;
import com.example.agentmonitor.model.Call;
import com.example.agentmonitor.model.InfrastructureTopology;
import com.example.agentmonitor.model.QueuedCall;
import com.example.agentmonitor.model.SystemStatus;
import com.example.agentmonitor.service.AgentService;
import com.example.agentmonitor.service.CallService;
import com.example.agentmonitor.service.InfrastructureService;
import com.example.agentmonitor.service.QueueService;
import com.example.agentmonitor.service.RedisHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for dashboard data.
 * Provides initial state on page load and fallback for WebSocket failures.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final AgentService agentService;
    private final CallService callService;
    private final QueueService queueService;
    private final RedisHealthService healthService;
    private final InfrastructureService infrastructureService;

    /**
     * Get system health status.
     */
    @GetMapping("/health")
    public ResponseEntity<SystemStatus> getHealth() {
        SystemStatus status = healthService.checkAndGetStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get all agents.
     */
    @GetMapping("/agents")
    public ResponseEntity<List<Agent>> getAgents() {
        try {
            List<Agent> agents = agentService.getAllAgents();
            return ResponseEntity.ok(agents);
        } catch (RedisUnavailableException e) {
            log.error("Redis unavailable when fetching agents", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Get agent summary.
     */
    @GetMapping("/agents/summary")
    public ResponseEntity<AgentSummary> getAgentSummary() {
        try {
            AgentSummary summary = agentService.getAgentSummary();
            return ResponseEntity.ok(summary);
        } catch (RedisUnavailableException e) {
            log.error("Redis unavailable when fetching summary", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Get active calls.
     */
    @GetMapping("/calls")
    public ResponseEntity<List<Call>> getCalls() {
        try {
            List<Call> calls = callService.getActiveCalls();
            return ResponseEntity.ok(calls);
        } catch (RedisUnavailableException e) {
            log.error("Redis unavailable when fetching calls", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Get queued calls with statistics.
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue() {
        try {
            List<QueuedCall> calls = queueService.getQueuedCalls();
            Map<String, Object> stats = queueService.getQueueStats();

            return ResponseEntity.ok(Map.of(
                "calls", calls,
                "stats", stats
            ));
        } catch (RedisUnavailableException e) {
            log.error("Redis unavailable when fetching queue", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Get infrastructure topology.
     */
    @GetMapping("/infrastructure")
    public ResponseEntity<InfrastructureTopology> getInfrastructure() {
        InfrastructureTopology topology = infrastructureService.getTopology();
        return ResponseEntity.ok(topology);
    }
}
