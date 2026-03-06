package com.fmr.ec3.oscc.tts.polly;

import com.fmr.ec3.oscc.tts.cache.TtsCache;

/**
 * Immutable configuration for the Polly TTS engine. Built via {@link #builder()}.
 */
public class PollyTtsConfig {

    private static final String DEFAULT_VOICE_ID = "Joanna";
    private static final String DEFAULT_ENGINE = "neural";
    private static final int DEFAULT_SAMPLE_RATE = 8000;

    private final String defaultVoiceId;
    private final String engine;
    private final int sampleRate;
    private final TtsCache cache;

    private PollyTtsConfig(Builder builder) {
        this.defaultVoiceId = builder.defaultVoiceId;
        this.engine = builder.engine;
        this.sampleRate = builder.sampleRate;
        this.cache = builder.cache;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getDefaultVoiceId() {
        return defaultVoiceId;
    }

    public String getEngine() {
        return engine;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public TtsCache getCache() {
        return cache;
    }

    public static final class Builder {

        private String defaultVoiceId = DEFAULT_VOICE_ID;
        private String engine = DEFAULT_ENGINE;
        private int sampleRate = DEFAULT_SAMPLE_RATE;
        private TtsCache cache;

        private Builder() {
        }

        public Builder defaultVoiceId(String defaultVoiceId) {
            this.defaultVoiceId = defaultVoiceId;
            return this;
        }

        public Builder engine(String engine) {
            this.engine = engine;
            return this;
        }

        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder cache(TtsCache cache) {
            this.cache = cache;
            return this;
        }

        public PollyTtsConfig build() {
            if (defaultVoiceId == null || defaultVoiceId.isBlank()) {
                throw new IllegalArgumentException("defaultVoiceId is required");
            }
            if (engine == null || engine.isBlank()) {
                throw new IllegalArgumentException("engine is required");
            }
            if (sampleRate <= 0) {
                throw new IllegalArgumentException("sampleRate must be positive");
            }
            return new PollyTtsConfig(this);
        }
    }
}
