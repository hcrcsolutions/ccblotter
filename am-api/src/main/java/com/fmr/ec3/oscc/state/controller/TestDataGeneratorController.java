package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.model.Agent;
import com.fmr.ec3.oscc.state.model.AgentState;
import com.fmr.ec3.oscc.state.model.AgentSummary;
import com.fmr.ec3.oscc.state.model.Call;
import com.fmr.ec3.oscc.state.service.AgentService;
import com.fmr.ec3.oscc.state.service.CallService;
import com.fmr.ec3.oscc.state.service.DataGeneratorService;
import com.fmr.ec3.oscc.state.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for test/demo data generation endpoints.
 * Only available when the "testDataAllowed" profile is active.
 */
@RestController
@RequestMapping("/api/test")
@Profile("testDataAllowed")
@RequiredArgsConstructor
@Slf4j
public class TestDataGeneratorController {

    private final AgentService agentService;
    private final CallService callService;
    private final DataGeneratorService dataGeneratorService;
    private final SimulationService simulationService;

    /**
     * Create a test agent (for demo purposes).
     */
    @PostMapping("/agents")
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
            return ResponseEntity.ok(agent);
        } catch (Exception e) {
            log.error("Failed to create test agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Start a test call (for demo purposes).
     */
    @PostMapping("/calls")
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
    @DeleteMapping("/calls/{callId}")
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
    @PutMapping("/agents/{agentId}/state")
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
    @DeleteMapping("/agents/{agentId}")
    public ResponseEntity<Void> deleteTestAgent(@PathVariable String agentId) {
        try {
            agentService.removeAgent(agentId);
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
    @PostMapping("/generate")
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
     * Generate a large dataset for load testing.
     * Writes directly to Redis with pipelining for speed.
     */
    @PostMapping("/generate-bulk")
    public ResponseEntity<Map<String, Object>> generateBulkData(@RequestBody Map<String, Integer> body) {
        try {
            int agentCount = body.getOrDefault("agentCount", 30000);
            int onCallCount = body.getOrDefault("onCallCount", 27000);
            int queueCount = body.getOrDefault("queueCount", 5000);

            if (onCallCount > agentCount) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "onCallCount cannot exceed agentCount"));
            }

            // Stop simulation if running
            simulationService.stopSimulation();

            dataGeneratorService.generateBulkData(agentCount, onCallCount, queueCount);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "agentCount", agentCount,
                "onCallCount", onCallCount,
                "queueCount", queueCount
            ));
        } catch (Exception e) {
            log.error("Failed to generate bulk data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Start the call center simulation.
     * Simulation creates realistic activity: new calls, call endings, state changes.
     */
    @PostMapping("/simulation/start")
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
    @PostMapping("/simulation/stop")
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
    @GetMapping("/simulation/status")
    public ResponseEntity<Map<String, Object>> getSimulationStatus() {
        return ResponseEntity.ok(Map.of(
            "running", simulationService.isRunning()
        ));
    }
}
