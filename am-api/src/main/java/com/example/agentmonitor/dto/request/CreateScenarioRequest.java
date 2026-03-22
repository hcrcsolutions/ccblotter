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
public class CreateScenarioRequest {

    @NotBlank(message = "Scenario name is required")
    @Size(max = 200, message = "Scenario name must not exceed 200 characters")
    private String name;

    private String description;
}
