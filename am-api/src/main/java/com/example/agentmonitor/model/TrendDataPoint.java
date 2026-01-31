package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A single data point for trend history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendDataPoint {
    private Instant timestamp;
    private int activeSessions;
    private double cpuPercent;
}
