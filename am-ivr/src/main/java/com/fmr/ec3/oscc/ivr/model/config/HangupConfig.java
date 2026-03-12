package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a HANGUP node. Built via {@link #builder()}.
 */
public class HangupConfig {

    private final String reason;
    private final String goodbyePrompt;

    private HangupConfig(Builder builder) {
        this.reason = builder.reason;
        this.goodbyePrompt = builder.goodbyePrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getReason() {
        return reason;
    }

    public String getGoodbyePrompt() {
        return goodbyePrompt;
    }

    public static final class Builder {

        private String reason;
        private String goodbyePrompt;

        private Builder() {
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder goodbyePrompt(String goodbyePrompt) {
            this.goodbyePrompt = goodbyePrompt;
            return this;
        }

        public HangupConfig build() {
            return new HangupConfig(this);
        }
    }
}
