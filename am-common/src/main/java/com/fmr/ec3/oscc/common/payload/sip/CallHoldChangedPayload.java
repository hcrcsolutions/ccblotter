package com.fmr.ec3.oscc.common.payload.sip;

public record CallHoldChangedPayload(
    String callId,
    String agentId,
    String newState,
    String sipServerId
) {}
