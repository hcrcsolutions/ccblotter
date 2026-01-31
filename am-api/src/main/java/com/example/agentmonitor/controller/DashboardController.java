package com.example.agentmonitor.controller;

import com.example.agentmonitor.exception.RedisUnavailableException;
import com.example.agentmonitor.model.*;
import com.example.agentmonitor.service.AgentService;
import com.example.agentmonitor.service.CallService;
import com.example.agentmonitor.service.DataGeneratorService;
import com.example.agentmonitor.service.InfrastructureService;
import com.example.agentmonitor.service.QueueService;
import com.example.agentmonitor.service.RedisHealthService;
import com.example.agentmonitor.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final DataGeneratorService dataGeneratorService;
    private final SimulationService simulationService;
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

    // ============= Test/Demo endpoints =============
    // These would be removed in production or protected

    /**
     * Create a test agent (for demo purposes).
     */
    @PostMapping("/test/agents")
    public ResponseEntity<Agent> createTestAgent(@RequestBody Map<String, String> body) {
        try {
            String id = body.get("id");
            String name = body.get("name");
            String stateStr = body.getOrDefault("state", "ONLINE");

            if (id == null || name == null) {
                return ResponseEntity.badRequest().build();
            }

            Agent agent = Agent.builder()
                    .id(id)
                    .name(name)
                    .state(AgentState.valueOf(stateStr))
                    .build();

            agentService.saveAgent(agent);
            agentService.broadcastAgents();
            agentService.broadcastSummary();

            return ResponseEntity.ok(agent);
        } catch (Exception e) {
            log.error("Failed to create test agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Start a test call (for demo purposes).
     */
    @PostMapping("/test/calls")
    public ResponseEntity<Call> startTestCall(@RequestBody Map<String, String> body) {
        try {
            String originator = body.get("originator");
            String agentId = body.get("agentId");

            if (originator == null || agentId == null) {
                return ResponseEntity.badRequest().build();
            }

            Call call = callService.startCall(originator, agentId);
            return ResponseEntity.ok(call);
        } catch (IllegalStateException e) {
            log.warn("Cannot start call: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Failed to start test call", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * End a test call (for demo purposes).
     */
    @DeleteMapping("/test/calls/{callId}")
    public ResponseEntity<Void> endTestCall(@PathVariable String callId) {
        try {
            callService.endCall(callId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to end test call", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update agent state (for demo purposes).
     */
    @PutMapping("/test/agents/{agentId}/state")
    public ResponseEntity<Void> updateAgentState(
            @PathVariable String agentId,
            @RequestBody Map<String, String> body) {
        try {
            String stateStr = body.get("state");
            if (stateStr == null) {
                return ResponseEntity.badRequest().build();
            }

            AgentState state = AgentState.valueOf(stateStr);
            if (state == AgentState.ON_CALL) {
                return ResponseEntity.badRequest().build(); // Use startCall instead
            }

            agentService.updateAgentState(agentId, state, null);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to update agent state", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a test agent (for demo purposes).
     */
    @DeleteMapping("/test/agents/{agentId}")
    public ResponseEntity<Void> deleteTestAgent(@PathVariable String agentId) {
        try {
            agentService.removeAgent(agentId);
            agentService.broadcastAgents();
            agentService.broadcastSummary();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to delete test agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate 400 agents with realistic data distribution.
     * Distribution: 50% Online, 25% On-Call, 15% Away, 10% Unavailable
     * Automatically starts simulation after generation.
     */
    @PostMapping("/test/generate")
    public ResponseEntity<Map<String, Object>> generateTestData() {
        try {
            log.info("Starting data generation...");

            // Stop simulation if running
            simulationService.stopSimulation();

            dataGeneratorService.generateData();

            AgentSummary summary = agentService.getAgentSummary();
            int callCount = callService.getActiveCallCount();

            // Auto-start simulation
            simulationService.startSimulation();

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "agentsCreated", summary.getTotal(),
                "callsCreated", callCount,
                "simulationStarted", true,
                "distribution", Map.of(
                    "online", summary.getOnline(),
                    "onCall", summary.getOnCall(),
                    "away", summary.getAway(),
                    "unavailable", summary.getUnavailable()
                )
            ));
        } catch (Exception e) {
            log.error("Failed to generate test data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Start the call center simulation.
     * Simulation creates realistic activity: new calls, call endings, state changes.
     */
    @PostMapping("/test/simulation/start")
    public ResponseEntity<Map<String, Object>> startSimulation() {
        simulationService.startSimulation();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Simulation started",
            "running", true
        ));
    }

    /**
     * Stop the call center simulation.
     */
    @PostMapping("/test/simulation/stop")
    public ResponseEntity<Map<String, Object>> stopSimulation() {
        simulationService.stopSimulation();
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Simulation stopped",
            "running", false
        ));
    }

    /**
     * Get simulation status.
     */
    @GetMapping("/test/simulation/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        return ResponseEntity.ok(Map.of(
            "running", simulationService.isRunning()
        ));
    }
}
