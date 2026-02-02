package com.example.agentmonitor.dto.response;

import com.example.agentmonitor.model.ServerHealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatResponse {

    private String id;
    private ServerHealthStatus status;
    private Instant lastHeartbeat;
    private Instant nextHeartbeatDue;
}
