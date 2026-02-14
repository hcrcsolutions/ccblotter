package com.fmr.ec3.oscc.common;

public final class EventType {

    private EventType() {}

    // SIP server events
    public static final String AGENT_LOGGED_IN = "AGENT_LOGGED_IN";
    public static final String AGENT_LOGGED_OUT = "AGENT_LOGGED_OUT";
    public static final String AGENT_BREAK_STARTED = "AGENT_BREAK_STARTED";
    public static final String AGENT_BREAK_ENDED = "AGENT_BREAK_ENDED";
    public static final String CALL_QUEUED = "CALL_QUEUED";
    public static final String CALL_ROUTED_TO_AGENT = "CALL_ROUTED_TO_AGENT";
    public static final String CALL_ENDED = "CALL_ENDED";
    public static final String CALL_ABANDONED = "CALL_ABANDONED";
    public static final String CALL_HOLD_CHANGED = "CALL_HOLD_CHANGED";

    // Infrastructure events
    public static final String NODE_REGISTERED = "NODE_REGISTERED";
    public static final String NODE_HEARTBEAT = "NODE_HEARTBEAT";
    public static final String NODE_DEREGISTERED = "NODE_DEREGISTERED";
    public static final String NODE_ALARM = "NODE_ALARM";
}
