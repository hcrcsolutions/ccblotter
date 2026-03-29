package com.fmr.ec3.oscc.state.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublishResultDto {

    private UUID flowId;
    private boolean published;
    private int version;
    private List<Map<String, Object>> validationIssues;
    private String message;
}
