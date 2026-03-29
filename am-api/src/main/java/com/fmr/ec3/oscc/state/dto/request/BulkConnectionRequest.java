package com.fmr.ec3.oscc.state.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkConnectionRequest {

    @NotEmpty(message = "Target IDs list cannot be empty")
    private List<String> targetIds;
}
