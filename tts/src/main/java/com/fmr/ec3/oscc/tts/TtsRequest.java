package com.fmr.ec3.oscc.tts;

/**
 * Immutable request for text-to-speech synthesis. Built via {@link #builder(String)}.
 */
public class TtsRequest {

    private final String text;
    private final String voiceId;
    private final boolean cacheable;

    private TtsRequest(Builder builder) {
        this.text = builder.text;
        this.voiceId = builder.voiceId;
        this.cacheable = builder.cacheable;
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public String getText() {
        return text;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public static final class Builder {

        private final String text;
        private String voiceId;
        private boolean cacheable;

        private Builder(String text) {
            this.text = text;
        }

        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }

        public Builder cacheable(boolean cacheable) {
            this.cacheable = cacheable;
            return this;
        }

        public TtsRequest build() {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("text is required");
            }
            return new TtsRequest(this);
        }
    }
}
