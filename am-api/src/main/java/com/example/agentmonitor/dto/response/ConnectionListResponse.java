package com.example.agentmonitor.dto.response;

import com.example.agentmonitor.model.ServerHealthStatus;
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
public class ConnectionListResponse {

    private String sourceId;
    private List<ConnectionDetail> connections;
    private int total;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionDetail {
        private String targetId;
        private String targetHostname;
        private ServerHealthStatus targetStatus;
        private Instant connectedAt;
    }
}
