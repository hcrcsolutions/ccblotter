package com.fmr.ec3.oscc.state.dto.response;

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
public class HeartbeatResponse {

    private String id;
    private ServerHealthStatus status;
    private Instant lastHeartbeat;
    private Instant nextHeartbeatDue;
}
