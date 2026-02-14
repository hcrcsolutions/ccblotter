package com.fmr.ec3.oscc.common;

public record EventEnvelope<T>(
    String eventId,
    long sequenceNumber,
    String sourceId,
    String correlationId,
    String partitionKey,
    String eventType,
    long timestamp,
    int schemaVersion,
    T payload
) {}
