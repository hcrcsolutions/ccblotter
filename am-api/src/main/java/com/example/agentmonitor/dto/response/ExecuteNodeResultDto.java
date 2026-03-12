package com.example.agentmonitor.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecuteNodeResultDto {

    private Map<String, Object> result;
    private Map<String, Object> updatedVariables;
    private String instructionType;
    private Map<String, Object> instructionParameters;
}
