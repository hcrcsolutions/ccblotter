package com.example.agentmonitor.dto.response;

import com.example.agentmonitor.model.ServerHealthStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeRegistrationResponse {

    private String id;
    private ServerHealthStatus status;
    private Instant registeredAt;
    private Instant reregisteredAt;
    private boolean reregistered;
    private int heartbeatIntervalSeconds;
    private int heartbeatTimeoutSeconds;
    private String message;
}
