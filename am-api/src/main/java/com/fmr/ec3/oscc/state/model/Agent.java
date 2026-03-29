package com.fmr.ec3.oscc.state.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a call center agent with their current state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    /**
     * Unique identifier for the agent.
     */
    private String id;

    /**
     * Display name of the agent.
     */
    private String name;

    /**
     * Current state of the agent.
     */
    private AgentState state;

    /**
     * Timestamp when the agent entered their current state.
     * Used for tracking time in state.
     */
    private Instant stateChangedAt;

    /**
     * ID of the current active call if agent is ON_CALL, null otherwise.
     * Invariant: Must be non-null if and only if state == ON_CALL.
     */
    private String currentCallId;

    // ============= Last Call Information =============
    // Populated when a call ends, provides historical context

    /**
     * Phone number/ID of the last caller this agent handled.
     */
    private String lastCallOriginator;

    /**
     * When the last call started.
     */
    private Instant lastCallStartTime;

    /**
     * When the last call ended.
     */
    private Instant lastCallEndTime;

    /**
     * Duration of the last call in seconds.
     */
    private Long lastCallDurationSeconds;
}
