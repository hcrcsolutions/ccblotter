package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Breakdown of sessions by direction and state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionBreakdown {
    // By direction
    private int inboundSessions;
    private int outboundSessions;
    // By state
    private int ivrSessions;
    private int queueSessions;
    private int agentSessions;
    private int onHoldSessions;
}
