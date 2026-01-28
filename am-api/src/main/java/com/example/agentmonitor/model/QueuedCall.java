package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a call waiting in queue for an available agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueuedCall {
    /**
     * Unique identifier for the queued call.
     */
    private String id;

    /**
     * Caller phone number or identifier.
     */
    private String originator;

    /**
     * When the call entered the queue.
     */
    private Instant queuedAt;

    /**
     * Priority level (1 = highest, 5 = lowest).
     * Higher priority calls are answered first.
     */
    private int priority;

    /**
     * Queue/skill the caller is waiting for (e.g., "Sales", "Support", "Billing").
     */
    private String skill;
}
