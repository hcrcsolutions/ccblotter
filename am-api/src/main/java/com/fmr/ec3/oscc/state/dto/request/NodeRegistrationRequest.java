package com.fmr.ec3.oscc.state.dto.request;

import com.fmr.ec3.oscc.state.model.InfraServerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeRegistrationRequest {

    @NotBlank(message = "Node ID is required")
    private String id;

    @NotNull(message = "Node type is required")
    private InfraServerType type;

    @NotBlank(message = "Hostname is required")
    private String hostname;

    @NotBlank(message = "IP address is required")
    private String ipAddress;

    @NotBlank(message = "Datacenter is required")
    private String datacenter;

    private String region;

    @Positive(message = "Max sessions must be positive")
    private int maxSessions;

    // Optional fields for TRUNK type
    private String carrierName;
    private String trunkGroup;

    // Additional metadata
    private Map<String, Object> metadata;
}
