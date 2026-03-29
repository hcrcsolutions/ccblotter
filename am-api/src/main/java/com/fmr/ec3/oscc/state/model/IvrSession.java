package com.fmr.ec3.oscc.state.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents an IVR session. Each session is 1:1 with a call
 * and consists of a sequence of IVR steps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrSession {

    /**
     * Unique identifier for the session.
     */
    private String id;

    /**
     * Associated call ID (1:1 relationship).
     */
    private String callId;

    /**
     * Caller phone number or ID.
     */
    private String originator;

    /**
     * IVR flow identifier (e.g. "main-menu", "billing-inquiry").
     */
    private String flowId;

    /**
     * Current state of the session.
     */
    private IvrState state;

    /**
     * Name of the current or last step executed.
     */
    private String currentStep;

    /**
     * Total number of steps executed so far.
     */
    private int stepCount;

    /**
     * When the session started.
     */
    private Instant startTime;

    /**
     * When the session ended (null if still active).
     */
    private Instant endTime;

    /**
     * Whether the caller has been authenticated.
     */
    private boolean authenticated;
}
