package com.fmr.ec3.oscc.common.payload.sip;

public record AgentBreakStartedPayload(
    String agentId,
    String breakType,
    String sipServerId
) {}
