package com.fmr.ec3.oscc.ivr.model.config;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for a SUB_FLOW node. Built via {@link #builder()}.
 */
public class SubFlowConfig {

    private final String subFlowId;
    private final Map<String, String> inputMappings;
    private final Map<String, String> outputMappings;

    private SubFlowConfig(Builder builder) {
        this.subFlowId = builder.subFlowId;
        this.inputMappings = builder.inputMappings == null
                ? Collections.emptyMap()
                : Map.copyOf(builder.inputMappings);
        this.outputMappings = builder.outputMappings == null
                ? Collections.emptyMap()
                : Map.copyOf(builder.outputMappings);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSubFlowId() {
        return subFlowId;
    }

    public Map<String, String> getInputMappings() {
        return inputMappings;
    }

    public Map<String, String> getOutputMappings() {
        return outputMappings;
    }

    public static final class Builder {

        private String subFlowId;
        private Map<String, String> inputMappings;
        private Map<String, String> outputMappings;

        private Builder() {
        }

        public Builder subFlowId(String subFlowId) {
            this.subFlowId = subFlowId;
            return this;
        }

        public Builder inputMappings(Map<String, String> inputMappings) {
            this.inputMappings = inputMappings;
            return this;
        }

        public Builder outputMappings(Map<String, String> outputMappings) {
            this.outputMappings = outputMappings;
            return this;
        }

        public SubFlowConfig build() {
            if (subFlowId == null || subFlowId.isBlank()) {
                throw new IllegalArgumentException("subFlowId is required");
            }
            return new SubFlowConfig(this);
        }
    }
}
