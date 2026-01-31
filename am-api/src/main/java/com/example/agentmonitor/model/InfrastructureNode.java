package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureNode {
    // Existing fields
    private String id;
    private InfraServerType type;
    private String hostname;
    private String ipAddress;
    private Instant startTime;
    private int activeSessions;
    private int maxSessions;
    private ServerHealthStatus healthStatus;

    // NEW: Datacenter grouping
    private String datacenter;        // e.g., "dc1", "dc2"
    private String region;            // e.g., "us-east", "us-west"

    // NEW: Quality metrics
    private NodeMetrics metrics;

    // NEW: Session breakdown (null for TRUNK/SBC)
    private SessionBreakdown sessionBreakdown;

    // NEW: Trend data (last 5 minutes, ~60 points at 5-sec intervals)
    private List<TrendDataPoint> trendHistory;

    // NEW: Trunk-specific fields
    private String carrierName;       // Only for TRUNK type
    private String trunkGroup;        // e.g., "primary", "backup"

    // NEW: Maintenance mode
    private boolean maintenanceMode;
}
