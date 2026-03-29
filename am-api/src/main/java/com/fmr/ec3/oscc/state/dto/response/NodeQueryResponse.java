package com.fmr.ec3.oscc.state.dto.response;

import com.fmr.ec3.oscc.state.model.InfraServerType;
import com.fmr.ec3.oscc.state.model.ServerHealthStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeQueryResponse {

    private List<NodeSummaryDto> nodes;
    private int total;
    private Filter filter;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filter {
        private InfraServerType type;
        private String datacenter;
        private ServerHealthStatus status;
    }
}
