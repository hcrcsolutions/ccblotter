package com.example.agentmonitor.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioVersionDto {

    private int version;
    private Instant createdAt;
    private String createdBy;
}
