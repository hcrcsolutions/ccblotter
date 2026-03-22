package com.example.agentmonitor.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunScenarioRequest {

    @Builder.Default
    private String timingMode = "COMPRESSED";
}
