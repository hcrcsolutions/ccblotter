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
public class FlowSummaryDto {

    private UUID id;
    private String name;
    private String description;
    private String businessUnit;
    private int version;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
