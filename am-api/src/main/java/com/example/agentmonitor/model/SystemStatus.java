package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents the system health status.
 * Used for fail-closed behavior when Redis is unavailable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatus {
    /**
     * Whether the Redis connection is healthy.
     * If false, the UI should display an error and refuse to show data.
     */
    private boolean redisConnected;

    /**
     * Timestamp of the last successful status check.
     */
    private Instant lastUpdated;

    /**
     * Error message if Redis is not connected.
     */
    private String errorMessage;
}
