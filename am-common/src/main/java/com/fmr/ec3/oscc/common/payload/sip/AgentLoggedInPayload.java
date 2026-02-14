package com.fmr.ec3.oscc.common.payload.sip;

import java.util.List;

public record AgentLoggedInPayload(
    String agentId,
    String agentName,
    List<String> skills,
    String sipServerId
) {}
