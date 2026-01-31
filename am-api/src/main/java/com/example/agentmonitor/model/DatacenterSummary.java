package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary statistics for a datacenter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatacenterSummary {
    private String id;
    private String region;
    private int totalNodes;
    private int healthyNodes;
    private int degradedNodes;
    private int unhealthyNodes;
    private int totalSessions;
    private int totalCapacity;
    private double utilizationPercent;
}
