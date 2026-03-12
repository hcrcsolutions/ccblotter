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
public class SaveFlowContentRequest {

    @NotNull(message = "Nodes list is required")
    private List<Map<String, Object>> nodes;

    @NotNull(message = "Edges list is required")
    private List<Map<String, Object>> edges;

    private List<Map<String, Object>> variables;

    private String createdBy;
}
