package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a COLLECT_DTMF node. Built via {@link #builder()}.
 */
public class CollectDtmfConfig {

    private final String prompt;
    private final int maxDigits;
    private final String terminatingKey;
    private final int timeoutSeconds;
    private final int interDigitTimeoutSeconds;
    private final String resultVariable;

    private CollectDtmfConfig(Builder builder) {
        this.prompt = builder.prompt;
        this.maxDigits = builder.maxDigits;
        this.terminatingKey = builder.terminatingKey;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.interDigitTimeoutSeconds = builder.interDigitTimeoutSeconds;
        this.resultVariable = builder.resultVariable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPrompt() {
        return prompt;
    }

    public int getMaxDigits() {
        return maxDigits;
    }

    public String getTerminatingKey() {
        return terminatingKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getInterDigitTimeoutSeconds() {
        return interDigitTimeoutSeconds;
    }

    public String getResultVariable() {
        return resultVariable;
    }

    public static final class Builder {

        private String prompt;
        private int maxDigits = 1;
        private String terminatingKey = "#";
        private int timeoutSeconds = 5;
        private int interDigitTimeoutSeconds = 3;
        private String resultVariable;

        private Builder() {
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder maxDigits(int maxDigits) {
            this.maxDigits = maxDigits;
            return this;
        }

        public Builder terminatingKey(String terminatingKey) {
            this.terminatingKey = terminatingKey;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder interDigitTimeoutSeconds(int interDigitTimeoutSeconds) {
            this.interDigitTimeoutSeconds = interDigitTimeoutSeconds;
            return this;
        }

        public Builder resultVariable(String resultVariable) {
            this.resultVariable = resultVariable;
            return this;
        }

        public CollectDtmfConfig build() {
            if (maxDigits <= 0) {
                throw new IllegalArgumentException("maxDigits must be positive");
            }
            return new CollectDtmfConfig(this);
        }
    }
}
