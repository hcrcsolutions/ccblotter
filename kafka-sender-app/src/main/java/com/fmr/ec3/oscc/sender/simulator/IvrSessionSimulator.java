package com.fmr.ec3.oscc.sender.simulator;

import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionEndedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrSessionStartedPayload;
import com.fmr.ec3.oscc.common.payload.ivr.IvrStepCompletedPayload;
import com.fmr.ec3.oscc.sender.EventProducer;
import com.fmr.ec3.oscc.sender.config.SimulationProperties;
import com.fmr.ec3.oscc.sender.model.SimulatedIvrSession;
import com.fmr.ec3.oscc.sender.model.SimulatedIvrSession.PlannedStep;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class IvrSessionSimulator {

    private static final Logger log = LoggerFactory.getLogger(IvrSessionSimulator.class);

    private final EventProducer eventProducer;
    private final SimulationProperties props;

    private final Map<String, SimulatedIvrSession> activeSessions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    private final Random random = new Random();

    private static final String SOURCE_ID = "media-sim-1";

    // Chance to start a new session per tick (%)
    private static final int NEW_SESSION_CHANCE_PERCENT = 30;
    // Chance an active session abandons (caller hangs up) per tick (%)
    private static final int ABANDON_CHANCE_PERCENT = 3;
    // Chance an active session fails (system error) per tick (%)
    private static final int FAIL_CHANCE_PERCENT = 1;
    // Max concurrent active sessions
    private static final int MAX_ACTIVE_SESSIONS = 25;

    private static final String[] FLOWS = {
        "main-menu", "billing-inquiry", "account-balance",
        "payment-processing", "tech-support", "card-activation"
    };
    private static final String[] AREA_CODES = {
        "212", "213", "312", "404", "415", "469", "503", "602",
        "650", "702", "713", "718", "773", "818", "917", "949"
    };

    // Step templates for building realistic flows
    private static final String[] PLAY_PROMPTS = {
        "Welcome Greeting", "Main Menu", "Billing Menu",
        "Account Options", "Hold Music", "Goodbye Message",
        "Service Announcement", "Hours of Operation"
    };
    private static final String[] SAY_PROMPTS = {
        "Your balance is $1,234.56", "Your payment of $500 was received",
        "Your next bill is due March 15th", "Please hold while we transfer you",
        "Authentication successful", "Your account number is verified",
        "No outstanding balance found", "Your request has been processed"
    };
    private static final String[] CAPTURE_NAMES = {
        "Enter your account number", "Enter your PIN",
        "Press 1 for billing, 2 for support, 3 for sales",
        "Enter the amount you wish to pay",
        "Say or enter your date of birth",
        "Enter your zip code"
    };
    private static final String[] CAPTURE_INPUTS = {
        "1", "2", "3", "1234", "5678", "0000", "9012",
        "January fifth", "checking account", "90210"
    };
    private static final int AUDIO_SAMPLE_COUNT = 5;

    public IvrSessionSimulator(EventProducer eventProducer, SimulationProperties props) {
        this.eventProducer = eventProducer;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        running.set(true);
        // Seed a few initial sessions
        for (int i = 0; i < 5; i++) {
            startNewSession();
        }
        log.info("IvrSessionSimulator initialized with {} seed sessions", activeSessions.size());
    }

    @Scheduled(fixedDelayString = "${simulation.ivr-tick-interval-ms:3000}")
    public void tick() {
        if (!running.get()) {
            return;
        }

        try {
            // 1. Maybe start new sessions
            if (activeSessions.size() < MAX_ACTIVE_SESSIONS && random.nextInt(100) < NEW_SESSION_CHANCE_PERCENT) {
                startNewSession();
            }

            // 2. Advance active sessions (emit step events)
            for (SimulatedIvrSession session : new ArrayList<>(activeSessions.values())) {
                advanceSession(session);
            }
        } catch (Exception e) {
            log.error("IVR simulation tick error: {}", e.getMessage(), e);
        }
    }

    public void start() {
        running.set(true);
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    private void startNewSession() {
        int num = sessionCounter.incrementAndGet();
        String sessionId = String.format("IVR-%05d", num);
        String callId = "CALL-" + UUID.randomUUID().toString().substring(0, 8);
        String originator = generatePhoneNumber();
        String flowId = FLOWS[random.nextInt(FLOWS.length)];
        long now = System.currentTimeMillis();

        List<PlannedStep> steps = generateFlowSteps(flowId);

        SimulatedIvrSession session = new SimulatedIvrSession(
            sessionId, callId, originator, flowId, now, steps);
        activeSessions.put(sessionId, session);

        eventProducer.send(
            KafkaTopics.IVR_EVENTS, sessionId,
            EventType.IVR_SESSION_STARTED, SOURCE_ID, sessionId,
            new IvrSessionStartedPayload(
                sessionId, callId, originator, flowId, now, SOURCE_ID)
        );

        log.debug("Started IVR session {} flow={}", sessionId, flowId);
    }

    private void advanceSession(SimulatedIvrSession session) {
        long now = System.currentTimeMillis();

        // Check for random abandon
        if (random.nextInt(100) < ABANDON_CHANCE_PERCENT) {
            endSession(session, "ABANDONED", now);
            return;
        }

        // Check for random failure
        if (random.nextInt(100) < FAIL_CHANCE_PERCENT) {
            endSession(session, "FAILED", now);
            return;
        }

        // Check if enough time has passed since last step
        PlannedStep planned = session.getCurrentPlannedStep();
        if (planned == null) {
            // No more steps — session completes
            endSession(session, "COMPLETED", now);
            return;
        }

        if (now - session.getLastStepEndMs() < planned.delayBeforeMs()) {
            return; // Not time yet
        }

        // Execute the step
        emitStepCompleted(session, planned, now);
        session.incrementStepCount();
        session.advanceStep();

        // Check for completion after last step
        if (!session.hasMoreSteps()) {
            // Delay the end event slightly
            long endTime = now + 200 + random.nextInt(500);
            endSession(session, "COMPLETED", endTime);
        }
    }

    private void emitStepCompleted(SimulatedIvrSession session, PlannedStep planned, long now) {
        String stepId = session.getSessionId() + "-S" + (session.getStepCount() + 1);
        int latencyMs = generateLatency();
        long stepDurationMs = 1000 + random.nextInt(5000); // 1-6 seconds
        long stepStart = now;
        long stepEnd = now + stepDurationMs;

        String input = null;
        String outcome = "success";
        String error = null;
        String audioUrl = null;
        int retryAttempt = 0;

        switch (planned.stepType()) {
            case "CAPTURE":
                input = planned.expectedInput();
                if (random.nextInt(100) < 10) {
                    outcome = "timeout";
                    input = null;
                    retryAttempt = 1;
                } else {
                    audioUrl = "/audio/samples/capture-" + (1 + random.nextInt(AUDIO_SAMPLE_COUNT)) + ".wav";
                }
                break;
            case "AUTHENTICATE":
                input = "****";
                if (random.nextInt(100) < 20) {
                    outcome = "auth-failed";
                    error = "Invalid PIN";
                    retryAttempt = random.nextInt(3);
                } else {
                    outcome = "auth-success";
                    session.setAuthenticated(true);
                    session.setState(SimulatedIvrSession.State.ACTIVE);
                }
                if ("auth-failed".equals(outcome)) {
                    session.setState(SimulatedIvrSession.State.AUTHENTICATING);
                }
                break;
            case "TRANSFER":
                session.setState(SimulatedIvrSession.State.TRANSFERRING);
                outcome = "in-progress";
                break;
            case "BRANCH":
                outcome = "branch-" + (1 + random.nextInt(3));
                break;
            default:
                break;
        }

        session.setLastStepEndMs(stepEnd);

        eventProducer.send(
            KafkaTopics.IVR_EVENTS, session.getSessionId(),
            EventType.IVR_STEP_COMPLETED, SOURCE_ID, stepId,
            new IvrStepCompletedPayload(
                stepId, session.getSessionId(), planned.stepType(), planned.stepName(),
                planned.prompt(), input, outcome,
                stepStart, stepEnd, latencyMs, retryAttempt, error, audioUrl, SOURCE_ID)
        );
    }

    private void endSession(SimulatedIvrSession session, String endState, long endTimeMs) {
        activeSessions.remove(session.getSessionId());

        eventProducer.send(
            KafkaTopics.IVR_EVENTS, session.getSessionId(),
            EventType.IVR_SESSION_ENDED, SOURCE_ID, session.getSessionId(),
            new IvrSessionEndedPayload(
                session.getSessionId(), session.getCallId(), endState,
                endTimeMs, session.getStepCount(), session.isAuthenticated(), SOURCE_ID)
        );

        log.debug("Ended IVR session {} state={}", session.getSessionId(), endState);
    }

    /**
     * Generate a realistic sequence of IVR steps for a flow.
     */
    private List<PlannedStep> generateFlowSteps(String flowId) {
        List<PlannedStep> steps = new ArrayList<>();
        int totalSteps = 3 + random.nextInt(6); // 3-8 steps

        // First step is always a welcome greeting
        steps.add(new PlannedStep("PLAY", "Welcome Greeting",
            "Welcome to our automated service.", null, 500 + random.nextInt(1000)));

        // Menu capture
        steps.add(new PlannedStep("CAPTURE",
            CAPTURE_NAMES[random.nextInt(CAPTURE_NAMES.length)],
            "Please make your selection.",
            CAPTURE_INPUTS[random.nextInt(CAPTURE_INPUTS.length)],
            1000 + random.nextInt(2000)));

        // Fill remaining steps with a realistic mix
        for (int i = 2; i < totalSteps - 1; i++) {
            steps.add(generateMiddleStep());
        }

        // Last step: transfer or goodbye
        if (random.nextInt(100) < 60) {
            steps.add(new PlannedStep("TRANSFER", "Agent Transfer",
                "Transferring to the next available agent.", null,
                1000 + random.nextInt(2000)));
        } else {
            steps.add(new PlannedStep("PLAY", "Goodbye Message",
                "Thank you for calling. Goodbye.", null,
                500 + random.nextInt(1000)));
        }

        return steps;
    }

    private PlannedStep generateMiddleStep() {
        long delay = 1500 + random.nextInt(3000);
        int roll = random.nextInt(100);

        if (roll < 20) {
            return new PlannedStep("PLAY",
                PLAY_PROMPTS[random.nextInt(PLAY_PROMPTS.length)],
                PLAY_PROMPTS[random.nextInt(PLAY_PROMPTS.length)],
                null, delay);
        } else if (roll < 35) {
            return new PlannedStep("SAY", "TTS Response",
                SAY_PROMPTS[random.nextInt(SAY_PROMPTS.length)],
                null, delay);
        } else if (roll < 55) {
            String captureName = CAPTURE_NAMES[random.nextInt(CAPTURE_NAMES.length)];
            return new PlannedStep("CAPTURE", captureName,
                captureName,
                CAPTURE_INPUTS[random.nextInt(CAPTURE_INPUTS.length)], delay);
        } else if (roll < 75) {
            return new PlannedStep("AUTHENTICATE", "PIN Entry",
                "Please enter your 4-digit PIN.", "****", delay);
        } else if (roll < 90) {
            return new PlannedStep("BRANCH", "Route Decision",
                null, null, delay);
        } else {
            return new PlannedStep("SAY", "Account Info",
                SAY_PROMPTS[random.nextInt(SAY_PROMPTS.length)],
                null, delay);
        }
    }

    private int generateLatency() {
        int roll = random.nextInt(100);
        if (roll < 70) {
            return 50 + random.nextInt(150);  // 50-200ms typical
        } else if (roll < 90) {
            return 200 + random.nextInt(300); // 200-500ms moderate
        } else {
            return 500 + random.nextInt(1500); // 500-2000ms spike
        }
    }

    private String generatePhoneNumber() {
        String areaCode = AREA_CODES[random.nextInt(AREA_CODES.length)];
        int exchange = 200 + random.nextInt(800);
        int subscriber = random.nextInt(10000);
        return String.format("(%s) %d-%04d", areaCode, exchange, subscriber);
    }
}
