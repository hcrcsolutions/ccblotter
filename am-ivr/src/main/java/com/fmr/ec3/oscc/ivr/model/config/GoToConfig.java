package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a GO_TO node. Built via {@link #builder()}.
 */
public class GoToConfig {

    private final String targetNodeId;

    private GoToConfig(Builder builder) {
        this.targetNodeId = builder.targetNodeId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public static final class Builder {

        private String targetNodeId;

        private Builder() {
        }

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public GoToConfig build() {
            if (targetNodeId == null || targetNodeId.isBlank()) {
                throw new IllegalArgumentException("targetNodeId is required");
            }
            return new GoToConfig(this);
        }
    }
}
