package com.fmr.ec3.oscc.common.payload.sip;

public record AgentBreakEndedPayload(
    String agentId,
    String sipServerId
) {}
