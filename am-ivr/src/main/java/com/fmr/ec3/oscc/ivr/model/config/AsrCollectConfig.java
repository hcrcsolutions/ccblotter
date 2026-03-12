package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for an ASR_COLLECT node. Built via {@link #builder()}.
 */
public class AsrCollectConfig {

    private final String prompt;
    private final String language;
    private final int timeoutSeconds;
    private final String resultVariable;
    private final boolean bargeIn;

    private AsrCollectConfig(Builder builder) {
        this.prompt = builder.prompt;
        this.language = builder.language;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.resultVariable = builder.resultVariable;
        this.bargeIn = builder.bargeIn;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPrompt() {
        return prompt;
    }

    public String getLanguage() {
        return language;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public String getResultVariable() {
        return resultVariable;
    }

    public boolean isBargeIn() {
        return bargeIn;
    }

    public static final class Builder {

        private String prompt;
        private String language = "en-US";
        private int timeoutSeconds = 5;
        private String resultVariable;
        private boolean bargeIn = true;

        private Builder() {
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder resultVariable(String resultVariable) {
            this.resultVariable = resultVariable;
            return this;
        }

        public Builder bargeIn(boolean bargeIn) {
            this.bargeIn = bargeIn;
            return this;
        }

        public AsrCollectConfig build() {
            if (resultVariable == null || resultVariable.isBlank()) {
                throw new IllegalArgumentException("resultVariable is required");
            }
            return new AsrCollectConfig(this);
        }
    }
}
