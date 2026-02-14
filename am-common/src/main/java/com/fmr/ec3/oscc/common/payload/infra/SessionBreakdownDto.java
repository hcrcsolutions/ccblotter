package com.fmr.ec3.oscc.common.payload.infra;

public record SessionBreakdownDto(
    int inboundSessions,
    int outboundSessions,
    int ivrSessions,
    int queueSessions,
    int agentSessions,
    int onHoldSessions
) {}
