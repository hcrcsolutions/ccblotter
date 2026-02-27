package com.fmr.ec3.oscc.common.payload.ivr;

/**
 * Emitted when an IVR step finishes executing.
 * Partition key: sessionId.
 */
public record IvrStepCompletedPayload(
    String stepId,
    String sessionId,
    String stepType,
    String stepName,
    String prompt,
    String input,
    String outcome,
    long startTimeMs,
    long endTimeMs,
    long latencyMs,
    int retryAttempt,
    String error,
    String audioUrl,
    String mediaServerId
) {}
