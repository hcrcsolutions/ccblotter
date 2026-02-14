package com.fmr.ec3.oscc.common.payload.infra;

public record NodeHeartbeatPayload(
    String nodeId,
    String nodeType,
    int activeSessions,
    int maxSessions,
    NodeMetricsDto metrics,
    SessionBreakdownDto sessionBreakdown
) {}
