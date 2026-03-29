package com.fmr.ec3.oscc.state.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkConnectionResponse {

    private String sourceId;
    private List<String> added;
    private List<String> removed;
    private List<FailedConnection> failed;
    private List<String> previousConnections;
    private List<String> currentConnections;
    private int totalConnections;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedConnection {
        private String targetId;
        private String reason;
    }
}
