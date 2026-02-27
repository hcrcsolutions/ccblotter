package com.fmr.ec3.oscc.common.payload.ivr;

/**
 * Emitted when a new IVR session begins (caller enters the IVR flow).
 * Partition key: sessionId.
 */
public record IvrSessionStartedPayload(
    String sessionId,
    String callId,
    String originator,
    String flowId,
    long startTimeMs,
    String mediaServerId
) {}
