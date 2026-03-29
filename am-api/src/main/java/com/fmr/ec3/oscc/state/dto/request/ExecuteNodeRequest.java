package com.fmr.ec3.oscc.state.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteNodeRequest {

    @NotBlank(message = "Flow ID is required")
    private String flowId;

    @NotBlank(message = "Node ID is required")
    private String nodeId;

    @NotBlank(message = "Call ID is required")
    private String callId;

    private String originator;

    @NotNull(message = "Variables map is required")
    private Map<String, Object> variables;

    private int stepCount;

    private String audioData;

    private String audioFormat;

    private Integer sampleRate;
}
