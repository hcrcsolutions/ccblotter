package com.fmr.ec3.oscc.ivr.model.config;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for an NLU_INTENT node. Built via {@link #builder()}.
 */
public class NluIntentConfig {

    private final String inputVariable;
    private final List<String> expectedIntents;
    private final String resultVariable;
    private final double confidenceThreshold;

    private NluIntentConfig(Builder builder) {
        this.inputVariable = builder.inputVariable;
        this.expectedIntents = builder.expectedIntents == null
                ? Collections.emptyList()
                : List.copyOf(builder.expectedIntents);
        this.resultVariable = builder.resultVariable;
        this.confidenceThreshold = builder.confidenceThreshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getInputVariable() {
        return inputVariable;
    }

    public List<String> getExpectedIntents() {
        return expectedIntents;
    }

    public String getResultVariable() {
        return resultVariable;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public static final class Builder {

        private String inputVariable;
        private List<String> expectedIntents;
        private String resultVariable;
        private double confidenceThreshold = 0.7;

        private Builder() {
        }

        public Builder inputVariable(String inputVariable) {
            this.inputVariable = inputVariable;
            return this;
        }

        public Builder expectedIntents(List<String> expectedIntents) {
            this.expectedIntents = expectedIntents;
            return this;
        }

        public Builder resultVariable(String resultVariable) {
            this.resultVariable = resultVariable;
            return this;
        }

        public Builder confidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
            return this;
        }

        public NluIntentConfig build() {
            if (inputVariable == null || inputVariable.isBlank()) {
                throw new IllegalArgumentException("inputVariable is required");
            }
            if (resultVariable == null || resultVariable.isBlank()) {
                throw new IllegalArgumentException("resultVariable is required");
            }
            if (confidenceThreshold < 0 || confidenceThreshold > 1) {
                throw new IllegalArgumentException("confidenceThreshold must be between 0 and 1");
            }
            return new NluIntentConfig(this);
        }
    }
}
