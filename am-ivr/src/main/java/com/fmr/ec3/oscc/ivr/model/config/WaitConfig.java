package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a WAIT node. Built via {@link #builder()}.
 */
public class WaitConfig {

    private final int durationSeconds;
    private final String musicOnHoldUrl;

    private WaitConfig(Builder builder) {
        this.durationSeconds = builder.durationSeconds;
        this.musicOnHoldUrl = builder.musicOnHoldUrl;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public String getMusicOnHoldUrl() {
        return musicOnHoldUrl;
    }

    public static final class Builder {

        private int durationSeconds = 1;
        private String musicOnHoldUrl;

        private Builder() {
        }

        public Builder durationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
            return this;
        }

        public Builder musicOnHoldUrl(String musicOnHoldUrl) {
            this.musicOnHoldUrl = musicOnHoldUrl;
            return this;
        }

        public WaitConfig build() {
            if (durationSeconds <= 0) {
                throw new IllegalArgumentException("durationSeconds must be positive");
            }
            return new WaitConfig(this);
        }
    }
}
