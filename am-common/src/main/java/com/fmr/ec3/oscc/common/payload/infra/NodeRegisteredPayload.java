package com.fmr.ec3.oscc.common.payload.infra;

public record NodeRegisteredPayload(
    String nodeId,
    String nodeType,
    String hostname,
    String ipAddress,
    String datacenter,
    String region,
    int maxSessions,
    String carrierName,
    String trunkGroup
) {}
