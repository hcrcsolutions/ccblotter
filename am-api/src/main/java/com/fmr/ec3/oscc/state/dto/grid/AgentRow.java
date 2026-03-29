package com.fmr.ec3.oscc.state.dto.grid;

import com.fmr.ec3.oscc.state.model.AgentState;
import com.fmr.ec3.oscc.state.model.CallState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Agent enriched with current call data for the agent grid.
 * Combines Agent fields with the active Call fields so the frontend
 * doesn't need a separate request to join them.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRow {

    // Agent fields
    private String id;

    private String name;

    private AgentState state;

    private Instant stateChangedAt;

    private String currentCallId;

    // Current call fields (from the Call matching currentCallId)
    private String currentCaller;

    private Instant callStartTime;

    private CallState callState;

    // Last call fields
    private String lastCallOriginator;

    private Instant lastCallStartTime;

    private Instant lastCallEndTime;

    private Long lastCallDurationSeconds;
}
