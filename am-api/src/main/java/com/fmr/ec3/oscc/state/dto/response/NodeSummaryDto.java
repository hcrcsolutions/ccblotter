package com.fmr.ec3.oscc.state.dto.response;

import com.fmr.ec3.oscc.state.model.InfraServerType;
import com.fmr.ec3.oscc.state.model.ServerHealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeSummaryDto {

    private String id;
    private InfraServerType type;
    private String hostname;
    private String ipAddress;
    private String datacenter;
    private String region;
    private ServerHealthStatus status;
    private int maxSessions;
    private int activeSessions;
    private int availableCapacity;
    private Instant lastHeartbeat;
}
