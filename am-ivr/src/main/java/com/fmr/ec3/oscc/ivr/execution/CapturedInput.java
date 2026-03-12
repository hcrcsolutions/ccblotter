package com.fmr.ec3.oscc.ivr.execution;

/**
 * Immutable representation of audio captured by the media server.
 * Built via {@link #builder()}.
 */
public class CapturedInput {

    private final String audioData;
    private final String audioFormat;
    private final int sampleRate;

    private CapturedInput(Builder builder) {
        this.audioData = builder.audioData;
        this.audioFormat = builder.audioFormat;
        this.sampleRate = builder.sampleRate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAudioData() {
        return audioData;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public static final class Builder {

        private String audioData;
        private String audioFormat = "wav";
        private int sampleRate = 16000;

        private Builder() {
        }

        public Builder audioData(String audioData) {
            this.audioData = audioData;
            return this;
        }

        public Builder audioFormat(String audioFormat) {
            this.audioFormat = audioFormat;
            return this;
        }

        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public CapturedInput build() {
            if (audioData == null) {
                throw new IllegalArgumentException("audioData is required");
            }
            return new CapturedInput(this);
        }
    }
}
