package com.fmr.ec3.oscc.common.payload.sip;

public record CallEndedPayload(
    String callId,
    String agentId,
    String originator,
    long callStartTimeMs,
    long callEndTimeMs,
    long durationSeconds,
    String reason,
    String sipServerId
) {}
