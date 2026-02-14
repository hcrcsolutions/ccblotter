package com.fmr.ec3.oscc.sender.model;

public class SimulatedQueuedCall {

    private final String callId;
    private final String originator;
    private final String skill;
    private final int priority;
    private final long queuedAtMs;

    public SimulatedQueuedCall(String callId, String originator, String skill,
                                int priority, long queuedAtMs) {
        this.callId = callId;
        this.originator = originator;
        this.skill = skill;
        this.priority = priority;
        this.queuedAtMs = queuedAtMs;
    }

    public String getCallId() { return callId; }
    public String getOriginator() { return originator; }
    public String getSkill() { return skill; }
    public int getPriority() { return priority; }
    public long getQueuedAtMs() { return queuedAtMs; }
}
