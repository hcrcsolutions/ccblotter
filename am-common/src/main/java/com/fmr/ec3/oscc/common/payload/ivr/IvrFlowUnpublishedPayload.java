package com.fmr.ec3.oscc.common.payload.ivr;

public record IvrFlowUnpublishedPayload(
    String flowId,
    long unpublishedAtMs
) {}
