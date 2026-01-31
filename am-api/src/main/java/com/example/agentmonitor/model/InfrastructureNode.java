package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfrastructureNode {
    private String id;
    private InfraServerType type;
    private String hostname;
    private String ipAddress;
    private Instant startTime;
    private int activeSessions;
    private int maxSessions;
    private ServerHealthStatus healthStatus;
}
