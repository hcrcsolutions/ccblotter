package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a PLAY_PROMPT node. Built via {@link #builder()}.
 */
public class PlayPromptConfig {

    private final String audioUrl;
    private final String ttsText;
    private final boolean bargeIn;

    private PlayPromptConfig(Builder builder) {
        this.audioUrl = builder.audioUrl;
        this.ttsText = builder.ttsText;
        this.bargeIn = builder.bargeIn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public String getTtsText() {
        return ttsText;
    }

    public boolean isBargeIn() {
        return bargeIn;
    }

    public static final class Builder {

        private String audioUrl;
        private String ttsText;
        private boolean bargeIn;

        private Builder() {
        }

        public Builder audioUrl(String audioUrl) {
            this.audioUrl = audioUrl;
            return this;
        }

        public Builder ttsText(String ttsText) {
            this.ttsText = ttsText;
            return this;
        }

        public Builder bargeIn(boolean bargeIn) {
            this.bargeIn = bargeIn;
            return this;
        }

        public PlayPromptConfig build() {
            if (audioUrl == null && ttsText == null) {
                throw new IllegalArgumentException("Either audioUrl or ttsText is required");
            }
            return new PlayPromptConfig(this);
        }
    }
}
