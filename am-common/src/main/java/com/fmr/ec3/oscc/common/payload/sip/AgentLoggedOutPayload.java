package com.fmr.ec3.oscc.common.payload.sip;

public record AgentLoggedOutPayload(
    String agentId,
    String reason,
    String sipServerId
) {}
