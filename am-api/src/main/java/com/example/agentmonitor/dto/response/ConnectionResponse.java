package com.example.agentmonitor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionResponse {

    private String edgeId;
    private String sourceId;
    private String targetId;
    private Instant createdAt;
}
