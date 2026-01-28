package com.example.agentmonitor.model;

/**
 * State of an active call.
 */
public enum CallState {
    /**
     * Agent and caller are actively talking.
     */
    TALKING,

    /**
     * Agent has placed the caller on hold.
     */
    ON_HOLD
}
