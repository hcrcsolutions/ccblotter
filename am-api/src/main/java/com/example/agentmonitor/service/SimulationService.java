package com.example.agentmonitor.service;

import com.example.agentmonitor.model.Agent;
import com.example.agentmonitor.model.AgentState;
import com.example.agentmonitor.model.Call;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simulates realistic call center activity over time.
 *
 * When enabled, this service will:
 * - Start new incoming calls (ONLINE agents receive calls)
 * - End calls after realistic durations
 * - Move agents to/from breaks (AWAY state)
 * - Simulate agents logging in/out (UNAVAILABLE state)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationService {

    private final AgentService agentService;
    private final CallService callService;

    private final AtomicBoolean simulationRunning = new AtomicBoolean(false);
    private final Random random = new Random();

    // Simulation parameters
    private static final int NEW_CALL_CHANCE_PERCENT = 60;      // High chance to start new calls
    private static final int END_CALL_CHANCE_PERCENT = 8;       // Lower chance to end calls
    private static final int BREAK_START_CHANCE_PERCENT = 2;    // Low chance for ONLINE agent to go on break
    private static final int BREAK_END_CHANCE_PERCENT = 30;     // High chance for AWAY agent to return
    private static final int LOGOUT_CHANCE_PERCENT = 1;         // Very low chance for ONLINE agent to log out
    private static final int LOGIN_CHANCE_PERCENT = 20;         // High chance for UNAVAILABLE agent to log in
    private static final double MIN_ON_CALL_RATIO = 0.50;       // Maintain at least 50% agents on call

    // Area codes for realistic phone numbers
    private static final String[] AREA_CODES = {
        "212", "213", "214", "312", "313", "404", "415", "469",
        "480", "503", "512", "602", "619", "650", "702", "713",
        "718", "720", "732", "773", "786", "818", "832", "847",
        "858", "917", "925", "949", "954", "972"
    };

    /**
     * Start the simulation.
     */
    public void startSimulation() {
        simulationRunning.set(true);
        log.info("Call center simulation STARTED");
    }

    /**
     * Stop the simulation.
     */
    public void stopSimulation() {
        simulationRunning.set(false);
        log.info("Call center simulation STOPPED");
    }

    /**
     * Check if simulation is running.
     */
    public boolean isRunning() {
        return simulationRunning.get();
    }

    /**
     * Main simulation loop - runs every 2 seconds when enabled.
     */
    @Scheduled(fixedRate = 2000)
    public void simulationTick() {
        if (!simulationRunning.get()) {
            return;
        }

        try {
            // Get current state
            List<Agent> agents = agentService.getAllAgents();
            List<Call> activeCalls = callService.getActiveCalls();

            // Calculate current on-call ratio
            long totalAgents = agents.size();
            long onCallAgents = agents.stream().filter(a -> a.getState() == AgentState.ON_CALL).count();
            double onCallRatio = totalAgents > 0 ? (double) onCallAgents / totalAgents : 0;

            // Simulate various activities with ratio awareness
            simulateIncomingCalls(agents, onCallRatio);
            simulateCallEndings(activeCalls, agents, onCallRatio);
            simulateBreaks(agents, onCallRatio);
            simulateLogins(agents);

        } catch (Exception e) {
            log.error("Simulation tick error: {}", e.getMessage());
        }
    }

    /**
     * Simulate incoming calls to ONLINE agents.
     * More aggressive when below minimum on-call ratio.
     */
    private void simulateIncomingCalls(List<Agent> agents, double currentOnCallRatio) {
        List<Agent> onlineAgents = agents.stream()
                .filter(a -> a.getState() == AgentState.ONLINE)
                .toList();

        if (onlineAgents.isEmpty()) {
            return;
        }

        // If below minimum ratio, be very aggressive about starting calls
        int effectiveChance = NEW_CALL_CHANCE_PERCENT;
        int maxCallsPerTick = 3;

        if (currentOnCallRatio < MIN_ON_CALL_RATIO) {
            effectiveChance = 95; // Almost always start calls
            maxCallsPerTick = 8;  // Start more calls per tick
            log.debug("SIM: Below {}% on-call ratio ({}%), aggressively starting calls",
                    (int)(MIN_ON_CALL_RATIO * 100), (int)(currentOnCallRatio * 100));
        }

        // Potentially start multiple calls per tick
        int callsToStart = 0;
        for (int i = 0; i < maxCallsPerTick; i++) {
            if (random.nextInt(100) < effectiveChance) {
                callsToStart++;
            }
        }

        for (int i = 0; i < callsToStart && i < onlineAgents.size(); i++) {
            Agent agent = onlineAgents.get(random.nextInt(onlineAgents.size()));

            // Double-check agent is still online (might have been picked already)
            Agent freshAgent = agentService.getAgent(agent.getId());
            if (freshAgent != null && freshAgent.getState() == AgentState.ONLINE) {
                String caller = generatePhoneNumber();
                try {
                    callService.startCall(caller, agent.getId());
                    log.info("SIM: New call from {} to agent {}", caller, agent.getName());
                } catch (Exception e) {
                    // Agent may have changed state, ignore
                }
            }
        }
    }

    /**
     * Simulate calls ending after varying durations.
     * Less likely to end calls when at or near minimum on-call ratio.
     */
    private void simulateCallEndings(List<Call> activeCalls, List<Agent> agents, double currentOnCallRatio) {
        // If at or below minimum ratio, drastically reduce call endings
        if (currentOnCallRatio <= MIN_ON_CALL_RATIO + 0.05) {
            // Only end very long calls (>15 min) when at minimum
            for (Call call : activeCalls) {
                long durationSeconds = java.time.Duration.between(
                    call.getStartTime(),
                    java.time.Instant.now()
                ).getSeconds();

                // Only end calls longer than 15 minutes, and rarely
                if (durationSeconds > 900 && random.nextInt(100) < 5) {
                    try {
                        callService.endCall(call.getId());
                        log.info("SIM: Long call ended for agent {} (duration: {}s)",
                                call.getAgentName(), durationSeconds);
                    } catch (Exception e) {
                        log.warn("SIM: Failed to end call {}: {}", call.getId(), e.getMessage());
                    }
                }
            }
            return;
        }

        for (Call call : activeCalls) {
            // Calculate call duration
            long durationSeconds = java.time.Duration.between(
                call.getStartTime(),
                java.time.Instant.now()
            ).getSeconds();

            // Longer calls have higher chance of ending
            int endChance = END_CALL_CHANCE_PERCENT;
            if (durationSeconds > 300) { // > 5 min
                endChance += 5;
            }
            if (durationSeconds > 600) { // > 10 min
                endChance += 10;
            }
            if (durationSeconds > 900) { // > 15 min
                endChance += 15;
            }

            if (random.nextInt(100) < endChance) {
                try {
                    callService.endCall(call.getId());
                    log.info("SIM: Call ended for agent {} (duration: {}s)",
                            call.getAgentName(), durationSeconds);
                } catch (Exception e) {
                    log.warn("SIM: Failed to end call {}: {}", call.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Simulate agents going on and returning from breaks.
     * Less likely to allow breaks when below minimum on-call ratio.
     */
    private void simulateBreaks(List<Agent> agents, double currentOnCallRatio) {
        for (Agent agent : agents) {
            if (agent.getState() == AgentState.ONLINE) {
                // Don't let agents go on break if we're below minimum on-call ratio
                if (currentOnCallRatio < MIN_ON_CALL_RATIO + 0.05) {
                    continue; // Skip break starts when we need more agents on call
                }

                // ONLINE agent might go on break
                if (random.nextInt(100) < BREAK_START_CHANCE_PERCENT) {
                    try {
                        agentService.updateAgentState(agent.getId(), AgentState.AWAY, null);
                        log.info("SIM: Agent {} went on break", agent.getName());
                    } catch (Exception e) {
                        // Ignore - agent may have started a call
                    }
                }
            } else if (agent.getState() == AgentState.AWAY) {
                // AWAY agent might return from break
                // Higher chance to return when below minimum ratio
                long awaySeconds = java.time.Duration.between(
                    agent.getStateChangedAt(),
                    java.time.Instant.now()
                ).getSeconds();

                int returnChance = BREAK_END_CHANCE_PERCENT;

                // Boost return chance when below minimum on-call ratio
                if (currentOnCallRatio < MIN_ON_CALL_RATIO) {
                    returnChance += 40; // Much higher chance to return
                }

                if (awaySeconds > 300) { // > 5 min break
                    returnChance += 15;
                }
                if (awaySeconds > 600) { // > 10 min break
                    returnChance += 25;
                }

                if (random.nextInt(100) < returnChance) {
                    try {
                        agentService.updateAgentState(agent.getId(), AgentState.ONLINE, null);
                        log.info("SIM: Agent {} returned from break", agent.getName());
                    } catch (Exception e) {
                        log.warn("SIM: Failed to return agent from break: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Simulate agents logging in and out.
     */
    private void simulateLogins(List<Agent> agents) {
        for (Agent agent : agents) {
            if (agent.getState() == AgentState.ONLINE) {
                // Small chance to log out
                if (random.nextInt(100) < LOGOUT_CHANCE_PERCENT) {
                    try {
                        agentService.updateAgentState(agent.getId(), AgentState.UNAVAILABLE, null);
                        log.info("SIM: Agent {} logged out", agent.getName());
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            } else if (agent.getState() == AgentState.UNAVAILABLE) {
                // UNAVAILABLE agent might log back in
                long unavailableSeconds = java.time.Duration.between(
                    agent.getStateChangedAt(),
                    java.time.Instant.now()
                ).getSeconds();

                // Higher chance to log in after being out longer
                int loginChance = LOGIN_CHANCE_PERCENT;
                if (unavailableSeconds > 120) {
                    loginChance += 10;
                }
                if (unavailableSeconds > 300) {
                    loginChance += 20;
                }

                if (random.nextInt(100) < loginChance) {
                    try {
                        agentService.updateAgentState(agent.getId(), AgentState.ONLINE, null);
                        log.info("SIM: Agent {} logged in", agent.getName());
                    } catch (Exception e) {
                        log.warn("SIM: Failed to log in agent: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Generate a realistic US phone number.
     */
    private String generatePhoneNumber() {
        String areaCode = AREA_CODES[random.nextInt(AREA_CODES.length)];
        int exchange = 200 + random.nextInt(800);
        int subscriber = random.nextInt(10000);
        return String.format("(%s) %d-%04d", areaCode, exchange, subscriber);
    }
}
