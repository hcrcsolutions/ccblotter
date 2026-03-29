package com.fmr.ec3.oscc.state.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents an active call in the call center.
 *
 * Invariants:
 * - Every call must have a valid agentId that corresponds to an existing agent
 * - The agent referenced by agentId must be in ON_CALL state
 * - startTime is set when the call is created and never modified
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Call {
    /**
     * Unique identifier for the call.
     */
    private String id;

    /**
     * Identifier of the call originator (caller phone number or ID).
     */
    private String originator;

    /**
     * ID of the agent handling this call.
     * Must reference a valid agent in ON_CALL state.
     */
    private String agentId;

    /**
     * Name of the agent handling this call (denormalized for display).
     */
    private String agentName;

    /**
     * Timestamp when the call was answered by the agent.
     * Duration is computed client-side as: now - startTime
     */
    private Instant startTime;

    /**
     * Current state of the call (TALKING or ON_HOLD).
     */
    private CallState state;
}
