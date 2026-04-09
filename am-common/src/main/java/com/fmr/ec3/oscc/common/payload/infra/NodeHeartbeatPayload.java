package com.fmr.ec3.oscc.common.payload.infra;

import java.util.List;

public record NodeHeartbeatPayload(
    String nodeId,
    String nodeType,
    int activeSessions,
    int maxSessions,
    NodeMetricsDto metrics,
    SessionBreakdownDto sessionBreakdown,
    List<GatewayStatusDto> gateways,
    List<CodecUsageDto> codecUsage
) {}
