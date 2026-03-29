package com.fmr.ec3.oscc.state.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
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
public class ScenarioRunDto {

    private UUID id;
    private UUID scenarioId;
    private int scenarioVersion;
    private String status;
    private String timingMode;
    private Instant startedAt;
    private Instant finishedAt;
    private String result;

    @JsonRawValue
    private String transcript;

    @JsonRawValue
    private String assertionResults;

    private String errorMessage;
    private Instant createdAt;
}
