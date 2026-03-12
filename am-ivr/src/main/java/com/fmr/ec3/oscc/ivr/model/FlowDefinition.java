package com.fmr.ec3.oscc.ivr.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Root IVR flow definition. Built via {@link #builder()}.
 */
@JsonDeserialize(builder = FlowDefinition.Builder.class)
public class FlowDefinition {

    private final String id;
    private final String name;
    private final String description;
    private final int version;
    private final FlowStatus status;
    private final String entryNodeId;
    private final List<FlowNode> nodes;
    private final List<FlowEdge> edges;
    private final List<FlowVariable> variables;
    private final int maxSessionDurationSeconds;
    private final int maxSteps;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FlowDefinition(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.version = builder.version;
        this.status = builder.status;
        this.entryNodeId = builder.entryNodeId;
        this.nodes = builder.nodes == null
                ? Collections.emptyList()
                : List.copyOf(builder.nodes);
        this.edges = builder.edges == null
                ? Collections.emptyList()
                : List.copyOf(builder.edges);
        this.variables = builder.variables == null
                ? Collections.emptyList()
                : List.copyOf(builder.variables);
        this.maxSessionDurationSeconds = builder.maxSessionDurationSeconds;
        this.maxSteps = builder.maxSteps;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getVersion() {
        return version;
    }

    public FlowStatus getStatus() {
        return status;
    }

    public String getEntryNodeId() {
        return entryNodeId;
    }

    public List<FlowNode> getNodes() {
        return nodes;
    }

    public List<FlowEdge> getEdges() {
        return edges;
    }

    public List<FlowVariable> getVariables() {
        return variables;
    }

    public int getMaxSessionDurationSeconds() {
        return maxSessionDurationSeconds;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private String name;
        private String description;
        private int version;
        private FlowStatus status = FlowStatus.DRAFT;
        private String entryNodeId;
        private List<FlowNode> nodes;
        private List<FlowEdge> edges;
        private List<FlowVariable> variables;
        private int maxSessionDurationSeconds = 600;
        private int maxSteps = 100;
        private Instant createdAt;
        private Instant updatedAt;

        Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder status(FlowStatus status) {
            this.status = status;
            return this;
        }

        public Builder entryNodeId(String entryNodeId) {
            this.entryNodeId = entryNodeId;
            return this;
        }

        public Builder nodes(List<FlowNode> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder edges(List<FlowEdge> edges) {
            this.edges = edges;
            return this;
        }

        public Builder variables(List<FlowVariable> variables) {
            this.variables = variables;
            return this;
        }

        public Builder maxSessionDurationSeconds(int maxSessionDurationSeconds) {
            this.maxSessionDurationSeconds = maxSessionDurationSeconds;
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public FlowDefinition build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            return new FlowDefinition(this);
        }
    }
}
