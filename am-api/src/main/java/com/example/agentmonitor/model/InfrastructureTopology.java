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
public class InfrastructureTopology {
    private List<InfrastructureNode> nodes;
    private List<InfrastructureEdge> edges;
    private Instant lastUpdated;
}
