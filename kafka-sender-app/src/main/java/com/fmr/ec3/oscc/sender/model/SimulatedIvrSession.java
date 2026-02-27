package com.fmr.ec3.oscc.sender.model;

import java.util.List;

public class SimulatedIvrSession {

    public enum State { ACTIVE, AUTHENTICATING, TRANSFERRING }

    private final String sessionId;
    private final String callId;
    private final String originator;
    private final String flowId;
    private final long startTimeMs;
    private State state;
    private int stepCount;
    private boolean authenticated;
    private long lastStepEndMs;
    private int pendingStepIndex;
    private final List<PlannedStep> plannedSteps;

    public SimulatedIvrSession(String sessionId, String callId, String originator,
                               String flowId, long startTimeMs,
                               List<PlannedStep> plannedSteps) {
        this.sessionId = sessionId;
        this.callId = callId;
        this.originator = originator;
        this.flowId = flowId;
        this.startTimeMs = startTimeMs;
        this.state = State.ACTIVE;
        this.stepCount = 0;
        this.authenticated = false;
        this.lastStepEndMs = startTimeMs;
        this.pendingStepIndex = 0;
        this.plannedSteps = plannedSteps;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCallId() {
        return callId;
    }

    public String getOriginator() {
        return originator;
    }

    public String getFlowId() {
        return flowId;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getStepCount() {
        return stepCount;
    }

    public void incrementStepCount() {
        this.stepCount++;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public long getLastStepEndMs() {
        return lastStepEndMs;
    }

    public void setLastStepEndMs(long lastStepEndMs) {
        this.lastStepEndMs = lastStepEndMs;
    }

    public int getPendingStepIndex() {
        return pendingStepIndex;
    }

    public void advanceStep() {
        this.pendingStepIndex++;
    }

    public boolean hasMoreSteps() {
        return pendingStepIndex < plannedSteps.size();
    }

    public PlannedStep getCurrentPlannedStep() {
        if (pendingStepIndex < plannedSteps.size()) {
            return plannedSteps.get(pendingStepIndex);
        }
        return null;
    }

    public List<PlannedStep> getPlannedSteps() {
        return plannedSteps;
    }

    /**
     * A pre-planned step in the IVR flow.
     */
    public record PlannedStep(
        String stepType,
        String stepName,
        String prompt,
        String expectedInput,
        long delayBeforeMs
    ) {}
}
