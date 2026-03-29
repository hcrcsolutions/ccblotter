package com.fmr.ec3.oscc.state.dto.request;

import com.fmr.ec3.oscc.state.model.NodeMetrics;
import com.fmr.ec3.oscc.state.model.SessionBreakdown;
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
