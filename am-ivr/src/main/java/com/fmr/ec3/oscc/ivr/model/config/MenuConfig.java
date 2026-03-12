package com.fmr.ec3.oscc.ivr.model.config;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for a MENU node. Built via {@link #builder()}.
 */
public class MenuConfig {

    private final String prompt;
    private final List<String> validOptions;
    private final int timeoutSeconds;
    private final int maxRetries;
    private final String invalidPrompt;
    private final String timeoutPrompt;
    private final String resultVariable;

    private MenuConfig(Builder builder) {
        this.prompt = builder.prompt;
        this.validOptions = builder.validOptions == null
                ? Collections.emptyList()
                : List.copyOf(builder.validOptions);
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxRetries = builder.maxRetries;
        this.invalidPrompt = builder.invalidPrompt;
        this.timeoutPrompt = builder.timeoutPrompt;
        this.resultVariable = builder.resultVariable;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getValidOptions() {
        return validOptions;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public String getInvalidPrompt() {
        return invalidPrompt;
    }

    public String getTimeoutPrompt() {
        return timeoutPrompt;
    }

    public String getResultVariable() {
        return resultVariable;
    }

    public static final class Builder {

        private String prompt;
        private List<String> validOptions;
        private int timeoutSeconds = 5;
        private int maxRetries = 3;
        private String invalidPrompt;
        private String timeoutPrompt;
        private String resultVariable;

        private Builder() {
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder validOptions(List<String> validOptions) {
            this.validOptions = validOptions;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder invalidPrompt(String invalidPrompt) {
            this.invalidPrompt = invalidPrompt;
            return this;
        }

        public Builder timeoutPrompt(String timeoutPrompt) {
            this.timeoutPrompt = timeoutPrompt;
            return this;
        }

        public Builder resultVariable(String resultVariable) {
            this.resultVariable = resultVariable;
            return this;
        }

        public MenuConfig build() {
            return new MenuConfig(this);
        }
    }
}
