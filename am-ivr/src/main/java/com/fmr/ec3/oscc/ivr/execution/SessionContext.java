package com.fmr.ec3.oscc.ivr.execution;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable session context for IVR flow execution. Built via {@link #builder()}.
 */
public class SessionContext {

    private final String callId;
    private final String originator;
    private final Map<String, Object> variables;
    private final int stepCount;
    private final Instant sessionStartTime;
    private final CapturedInput capturedInput;

    private SessionContext(Builder builder) {
        this.callId = builder.callId;
        this.originator = builder.originator;
        this.variables = builder.variables == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.variables));
        this.stepCount = builder.stepCount;
        this.sessionStartTime = builder.sessionStartTime;
        this.capturedInput = builder.capturedInput;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getCallId() {
        return callId;
    }

    public String getOriginator() {
        return originator;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public int getStepCount() {
        return stepCount;
    }

    public Instant getSessionStartTime() {
        return sessionStartTime;
    }

    public CapturedInput getCapturedInput() {
        return capturedInput;
    }

    public static final class Builder {

        private String callId;
        private String originator;
        private Map<String, Object> variables;
        private int stepCount;
        private Instant sessionStartTime;
        private CapturedInput capturedInput;

        private Builder() {
        }

        public Builder callId(String callId) {
            this.callId = callId;
            return this;
        }

        public Builder originator(String originator) {
            this.originator = originator;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Builder stepCount(int stepCount) {
            this.stepCount = stepCount;
            return this;
        }

        public Builder sessionStartTime(Instant sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
            return this;
        }

        public Builder capturedInput(CapturedInput capturedInput) {
            this.capturedInput = capturedInput;
            return this;
        }

        public SessionContext build() {
            if (callId == null || callId.isBlank()) {
                throw new IllegalArgumentException("callId is required");
            }
            return new SessionContext(this);
        }
    }
}
