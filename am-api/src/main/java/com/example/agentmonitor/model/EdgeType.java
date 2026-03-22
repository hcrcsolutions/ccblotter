package com.example.agentmonitor.model;

/**
 * Type of dependency edge between scenario steps.
 */
public enum EdgeType {

    /**
     * Sequential execution — target runs after source completes.
     */
    SEQUENCE,

    /**
     * Synchronization — target waits for source and receives its result data.
     */
    SYNC,

    /**
     * Causation — source completion triggers target execution.
     */
    TRIGGER,

    /**
     * Blocking wait — target blocks until source completes.
     */
    WAIT_FOR
}
