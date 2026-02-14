package com.fmr.ec3.oscc.sender.model;

public class SimulatedCall {

    private final String callId;
    private final String agentId;
    private final String agentName;
    private final String originator;
    private final long startTimeMs;
    private boolean onHold;

    public SimulatedCall(String callId, String agentId, String agentName,
                         String originator, long startTimeMs) {
        this.callId = callId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.originator = originator;
        this.startTimeMs = startTimeMs;
    }

    public String getCallId() { return callId; }
    public String getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public String getOriginator() { return originator; }
    public long getStartTimeMs() { return startTimeMs; }
    public boolean isOnHold() { return onHold; }
    public void setOnHold(boolean onHold) { this.onHold = onHold; }
}
