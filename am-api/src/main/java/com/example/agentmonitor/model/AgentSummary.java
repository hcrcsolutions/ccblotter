package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of agent states for dashboard display.
 *
 * Invariant: online + onCall + away + unavailable == total
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSummary {
    /**
     * Number of agents in ONLINE state (ready for calls).
     */
    private int online;

    /**
     * Number of agents in ON_CALL state (handling calls).
     */
    private int onCall;

    /**
     * Number of agents in AWAY state (on break).
     */
    private int away;

    /**
     * Number of agents in UNAVAILABLE state (logged out).
     */
    private int unavailable;

    /**
     * Total number of agents in the system.
     */
    private int total;

    /**
     * Validates the summary invariant.
     * @throws IllegalStateException if counts don't sum to total
     */
    public void validate() {
        int sum = online + onCall + away + unavailable;
        if (sum != total) {
            throw new IllegalStateException(
                String.format("AgentSummary invariant violated: %d + %d + %d + %d = %d != %d",
                    online, onCall, away, unavailable, sum, total));
        }
    }
}
