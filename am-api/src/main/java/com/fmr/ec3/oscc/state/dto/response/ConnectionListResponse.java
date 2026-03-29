package com.fmr.ec3.oscc.state.dto.response;

import com.fmr.ec3.oscc.state.model.ServerHealthStatus;
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
