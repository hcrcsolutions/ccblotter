package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quality metrics for an infrastructure node.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeMetrics {
    private double cpuPercent;
    private double memoryPercent;
    private int latencyMs;
    private int jitterMs;
    private double packetLossPercent;
    private double errorRate;        // Failed calls per minute
    private int mosScore;            // Mean Opinion Score (1-5 scale, x10 for precision: 10-50)
}
