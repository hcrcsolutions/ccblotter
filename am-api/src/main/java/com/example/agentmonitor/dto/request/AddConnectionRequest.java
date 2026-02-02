package com.example.agentmonitor.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddConnectionRequest {

    @NotBlank(message = "Target ID is required")
    private String targetId;

    private Integer bandwidthMbps;
}
