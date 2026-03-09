package com.fmr.ec3.oscc.audiostore;

/**
 * Immutable request for deleting an audio. Built via {@link #builder()}.
 */
public class DeleteRequest {

    private final String sessionId;
    private final String audioId;

    private DeleteRequest(Builder builder) {
        this.sessionId = builder.sessionId;
        this.audioId = builder.audioId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAudioId() {
        return audioId;
    }

    public static final class Builder {

        private String sessionId;
        private String audioId;

        private Builder() {
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder audioId(String audioId) {
            this.audioId = audioId;
            return this;
        }

        public DeleteRequest build() {
            KeyValidator.validate(sessionId, "sessionId");
            KeyValidator.validate(audioId, "audioId");
            return new DeleteRequest(this);
        }
    }
}
