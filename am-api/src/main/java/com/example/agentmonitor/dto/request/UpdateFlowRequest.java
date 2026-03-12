package com.example.agentmonitor.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFlowRequest {

    @NotBlank(message = "Flow name is required")
    @Size(max = 200, message = "Flow name must not exceed 200 characters")
    private String name;

    private String description;

    private String entryNodeId;

    private Integer maxSessionDurationSeconds;

    private Integer maxSteps;
}
