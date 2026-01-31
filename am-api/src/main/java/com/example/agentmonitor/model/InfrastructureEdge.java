package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureEdge {
    private String id;
    private String sourceId;
    private String targetId;

    // NEW: Edge metrics
    private Integer bandwidthMbps;    // Connection bandwidth
    private Integer latencyMs;        // Edge latency
    private Integer activeFlows;      // Active sessions on this edge
}
