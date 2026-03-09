package com.fmr.ec3.oscc.audiostore;

/**
 * Immutable request for listing audios in a session. Built via {@link #builder()}.
 */
public class ListRequest {

    private final String sessionId;
    private final int maxResults;

    private ListRequest(Builder builder) {
        this.sessionId = builder.sessionId;
        this.maxResults = builder.maxResults;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public static final class Builder {

        private String sessionId;
        private int maxResults = 1000;

        private Builder() {
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public ListRequest build() {
            KeyValidator.validate(sessionId, "sessionId");
            if (maxResults <= 0 || maxResults > 1000) {
                throw new IllegalArgumentException("maxResults must be between 1 and 1000");
            }
            return new ListRequest(this);
        }
    }
}
