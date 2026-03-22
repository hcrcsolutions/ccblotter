package com.example.agentmonitor.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveScenarioContentRequest {

    @NotNull(message = "Content is required")
    private Map<String, Object> content;

    private String createdBy;
}
