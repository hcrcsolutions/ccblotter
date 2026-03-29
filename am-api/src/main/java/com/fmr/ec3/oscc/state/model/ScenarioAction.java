package com.fmr.ec3.oscc.state.model;

/**
 * Actions that actors can perform during a test scenario execution.
 */
public enum ScenarioAction {

    // --- Caller actions ---

    DIAL_IN,
    IVR_INPUT,
    WAIT_IN_QUEUE,
    HANG_UP,

    // --- Agent actions ---

    LOGIN,
    GO_ONLINE,
    GO_AWAY,
    ANSWER_CALL,
    HOLD_CALL,
    RESUME_CALL,
    END_CALL,
    LOGOUT,

    // --- Supervisor actions ---

    MONITOR,
    WHISPER,
    BARGE_IN,
    END_MONITOR,

    // --- Common actions ---

    WAIT
}
