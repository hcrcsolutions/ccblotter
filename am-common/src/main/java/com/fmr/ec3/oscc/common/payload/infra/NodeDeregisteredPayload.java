package com.fmr.ec3.oscc.common.payload.infra;

public record NodeDeregisteredPayload(
    String nodeId,
    String reason
) {}
