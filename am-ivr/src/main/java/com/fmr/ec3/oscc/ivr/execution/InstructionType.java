package com.fmr.ec3.oscc.ivr.execution;

/**
 * Types of instructions that the IVR service can send to the routing server.
 */
public enum InstructionType {

    /**
     * Routing server must capture caller audio and re-call with the captured data.
     */
    CAPTURE_INPUT
}
