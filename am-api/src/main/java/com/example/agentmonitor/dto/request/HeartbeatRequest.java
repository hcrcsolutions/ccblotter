package com.example.agentmonitor.dto.request;

import com.example.agentmonitor.model.NodeMetrics;
import com.example.agentmonitor.model.SessionBreakdown;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {

    private Integer activeSessions;

    private NodeMetrics metrics;

    private SessionBreakdown sessionBreakdown;
}
