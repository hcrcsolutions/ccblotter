package com.fmr.ec3.oscc.state.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddConnectionRequest {

    @NotBlank(message = "Target ID is required")
    private String targetId;

    private Integer bandwidthMbps;
}
