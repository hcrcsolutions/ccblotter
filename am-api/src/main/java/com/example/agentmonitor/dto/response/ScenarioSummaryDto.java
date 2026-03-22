package com.example.agentmonitor.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScenarioSummaryDto {

    private UUID id;
    private String name;
    private String description;
    private int version;
    private String status;
    private String lastRunResult;
    private Instant lastRunAt;
    private Instant createdAt;
    private Instant updatedAt;
}
