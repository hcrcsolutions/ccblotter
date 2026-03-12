package com.fmr.ec3.oscc.common.payload.ivr;

public record IvrFlowPublishedPayload(
    String flowId,
    String flowDefinitionJson,
    int version,
    long publishedAtMs
) {}
