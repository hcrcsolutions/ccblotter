package com.fmr.ec3.oscc.common.payload.sip;

public record CallAbandonedPayload(
    String callId,
    String originator,
    long queuedAtMs,
    long abandonedAtMs,
    long waitDurationSeconds,
    String sipServerId
) {}
