package com.fmr.ec3.oscc.state.model;

/**
 * Represents the possible states of a call center agent.
 *
 * State transitions:
 * - UNAVAILABLE -> ONLINE: Agent logs in
 * - ONLINE -> ON_CALL: Agent answers a call
 * - ON_CALL -> ONLINE: Call ends
 * - ONLINE -> AWAY: Agent takes a break
 * - AWAY -> ONLINE: Agent returns from break
 * - Any state -> UNAVAILABLE: Agent logs out or disconnects
 */
public enum AgentState {
    /**
     * Agent is logged in and ready to receive calls.
     */
    ONLINE,

    /**
     * Agent is currently handling an active call.
     * Invariant: Agent must have exactly one associated active call.
     */
    ON_CALL,

    /**
     * Agent is temporarily away (break, lunch, personal time).
     * Agent is logged in but not available for calls.
     */
    AWAY,

    /**
     * Agent is not available - logged out, system issue, or not working.
     */
    UNAVAILABLE
}
