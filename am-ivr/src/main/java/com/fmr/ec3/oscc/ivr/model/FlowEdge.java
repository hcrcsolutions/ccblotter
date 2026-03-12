package com.fmr.ec3.oscc.ivr.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Immutable directed edge between flow nodes. Built via {@link #builder()}.
 */
@JsonDeserialize(builder = FlowEdge.Builder.class)
public class FlowEdge {

    private final String id;
    private final String sourceNodeId;
    private final String targetNodeId;
    private final String condition;

    private FlowEdge(Builder builder) {
        this.id = builder.id;
        this.sourceNodeId = builder.sourceNodeId;
        this.targetNodeId = builder.targetNodeId;
        this.condition = builder.condition;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getCondition() {
        return condition;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String sourceNodeId;
        private String targetNodeId;
        private String condition;

        Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sourceNodeId(String sourceNodeId) {
            this.sourceNodeId = sourceNodeId;
            return this;
        }

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        public FlowEdge build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (sourceNodeId == null || sourceNodeId.isBlank()) {
                throw new IllegalArgumentException("sourceNodeId is required");
            }
            if (targetNodeId == null || targetNodeId.isBlank()) {
                throw new IllegalArgumentException("targetNodeId is required");
            }
            return new FlowEdge(this);
        }
    }
}
