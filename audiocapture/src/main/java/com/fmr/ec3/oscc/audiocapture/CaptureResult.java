package com.fmr.ec3.oscc.audiocapture;

/**
 * Immutable result of a completed audio capture.
 */
public class CaptureResult {

    private final String url;
    private final long durationMs;
    private final long fileSizeBytes;
    private final boolean empty;
    private final String sessionId;
    private final String stepId;

    public CaptureResult(String url, long durationMs, long fileSizeBytes,
                         boolean empty, String sessionId, String stepId) {
        this.url = url;
        this.durationMs = durationMs;
        this.fileSizeBytes = fileSizeBytes;
        this.empty = empty;
        this.sessionId = sessionId;
        this.stepId = stepId;
    }

    public static CaptureResult empty(String sessionId, String stepId) {
        return new CaptureResult(null, 0, 0, true, sessionId, stepId);
    }

    public String getUrl() {
        return url;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public boolean isEmpty() {
        return empty;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getStepId() {
        return stepId;
    }
}
