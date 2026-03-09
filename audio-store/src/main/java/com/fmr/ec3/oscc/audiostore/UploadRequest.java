package com.fmr.ec3.oscc.audiostore;

/**
 * Immutable request for uploading an audio. Built via {@link #builder()}.
 */
public class UploadRequest {

    private final String sessionId;
    private final String audioId;
    private final byte[] data;
    private final String contentType;

    private UploadRequest(Builder builder) {
        this.sessionId = builder.sessionId;
        this.audioId = builder.audioId;
        this.data = builder.data;
        this.contentType = builder.contentType;
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

    public byte[] getData() {
        return data;
    }

    public String getContentType() {
        return contentType;
    }

    public static final class Builder {

        private String sessionId;
        private String audioId;
        private byte[] data;
        private String contentType = "application/octet-stream";

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

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public UploadRequest build() {
            KeyValidator.validate(sessionId, "sessionId");
            KeyValidator.validate(audioId, "audioId");
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("data is required");
            }
            return new UploadRequest(this);
        }
    }
}
