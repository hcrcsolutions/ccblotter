package com.fmr.ec3.oscc.common.payload.ivr;

/**
 * Emitted when an IVR session ends (normal completion, caller abandon, or failure).
 * Partition key: sessionId.
 */
public record IvrSessionEndedPayload(
    String sessionId,
    String callId,
    String endState,
    long endTimeMs,
    int stepCount,
    boolean authenticated,
    String mediaServerId
) {}
