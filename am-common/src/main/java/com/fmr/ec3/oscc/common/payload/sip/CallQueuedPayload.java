package com.fmr.ec3.oscc.common.payload.sip;

public record CallQueuedPayload(
    String callId,
    String originator,
    String skill,
    int priority,
    long queuedAtMs,
    String sipServerId
) {}
