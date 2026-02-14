package com.fmr.ec3.oscc.common.payload.infra;

public record NodeAlarmPayload(
    String nodeId,
    String alarmType,
    String severity,
    double thresholdValue,
    double actualValue
) {}
