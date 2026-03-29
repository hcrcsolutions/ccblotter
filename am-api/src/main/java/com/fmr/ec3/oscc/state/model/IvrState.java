package com.fmr.ec3.oscc.state.model;

/**
 * Represents the possible states of an IVR session.
 *
 * State transitions:
 * - ACTIVE -> AUTHENTICATING: Session enters authentication flow
 * - AUTHENTICATING -> ACTIVE: Authentication completes (pass or fail)
 * - ACTIVE -> TRANSFERRING: Session begins agent transfer
 * - ACTIVE -> COMPLETED: Session finishes normally
 * - ACTIVE -> ABANDONED: Caller hangs up mid-session
 * - Any state -> FAILED: System error or timeout
 */
public enum IvrState {
    /**
     * Session is actively processing IVR steps.
     */
    ACTIVE,

    /**
     * Session is in an authentication step (e.g. PIN entry).
     */
    AUTHENTICATING,

    /**
     * Session is transferring the caller to an agent or queue.
     */
    TRANSFERRING,

    /**
     * Session finished normally (e.g. transfer complete, info delivered).
     */
    COMPLETED,

    /**
     * Caller disconnected before session completed.
     */
    ABANDONED,

    /**
     * Session ended due to a system error or timeout.
     */
    FAILED
}
