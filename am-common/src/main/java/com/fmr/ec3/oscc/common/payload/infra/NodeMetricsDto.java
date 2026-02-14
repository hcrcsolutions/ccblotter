package com.fmr.ec3.oscc.common.payload.infra;

public record NodeMetricsDto(
    double cpuPercent,
    double memoryPercent,
    int latencyMs,
    int jitterMs,
    double packetLossPercent,
    double errorRate,
    int mosScore
) {}
