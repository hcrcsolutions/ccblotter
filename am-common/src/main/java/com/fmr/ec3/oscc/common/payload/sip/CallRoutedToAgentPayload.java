package com.fmr.ec3.oscc.common.payload.sip;

public record CallRoutedToAgentPayload(
    String callId,
    String agentId,
    String agentName,
    String originator,
    String queuedCallId,
    String skill,
    long callStartTimeMs,
    String sipServerId
) {}
