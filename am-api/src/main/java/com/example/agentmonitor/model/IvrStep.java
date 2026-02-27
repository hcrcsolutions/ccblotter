package com.example.agentmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a single step in an IVR session.
 * Steps are stored in order in a Redis list keyed by session ID.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrStep {

    /**
     * Unique identifier for this step.
     */
    private String id;

    /**
     * ID of the parent session.
     */
    private String sessionId;

    /**
     * Type of step (PLAY, SAY, CAPTURE, etc.).
     */
    private IvrStepType stepType;

    /**
     * Human-readable name for this step (e.g. "Welcome Greeting", "PIN Entry").
     */
    private String stepName;

    /**
     * Text or audio prompt played to the caller (null for non-prompt steps).
     */
    private String prompt;

    /**
     * Caller input captured (DTMF digits or speech text, null for non-capture steps).
     */
    private String input;

    /**
     * Outcome of the step (e.g. "success", "timeout", "no-match", "auth-failed").
     */
    private String outcome;

    /**
     * When this step started.
     */
    private Instant startTime;

    /**
     * When this step ended.
     */
    private Instant endTime;

    /**
     * AI/system response time in milliseconds.
     */
    private long latencyMs;

    /**
     * Retry attempt number (0 for first attempt).
     */
    private int retryAttempt;

    /**
     * Error message if the step failed (null on success).
     */
    private String error;

    /**
     * URL for captured utterance audio recording (null for non-CAPTURE steps).
     */
    private String audioUrl;
}
