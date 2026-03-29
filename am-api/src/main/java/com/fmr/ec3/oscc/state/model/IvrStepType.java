package com.fmr.ec3.oscc.state.model;

/**
 * Types of steps in an IVR session flow.
 * Matches the grammar specification in docs/ivr-grammar.md.
 */
public enum IvrStepType {
    /**
     * Play a pre-recorded audio prompt.
     */
    PLAY,

    /**
     * Speak synthesized text (TTS).
     */
    SAY,

    /**
     * Capture caller input (DTMF or speech).
     */
    CAPTURE,

    /**
     * Authenticate the caller (PIN, account number, etc.).
     */
    AUTHENTICATE,

    /**
     * Transfer caller to an agent, queue, or external number.
     */
    TRANSFER,

    /**
     * End the call.
     */
    HANGUP,

    /**
     * Conditional branch based on input or state.
     */
    BRANCH
}
