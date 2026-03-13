package com.fmr.ec3.oscc.ivr.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable node in an IVR flow. Built via {@link #builder()}.
 * Node-type-specific configuration is stored as a generic map in {@code config}.
 *
 * <p>The raw content JSON may contain editor-only properties
 * such as {@code position} that are not part of the runtime
 * model; they are silently ignored during deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = FlowNode.Builder.class)
public class FlowNode {

    private final String id;
    private final NodeType type;
    private final String label;
    private final Map<String, Object> config;

    private FlowNode(Builder builder) {
        this.id = builder.id;
        this.type = builder.type;
        this.label = builder.label;
        this.config = builder.config == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.config));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String id;
        private NodeType type;
        private String label;
        private Map<String, Object> config;

        Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }

        public FlowNode build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            return new FlowNode(this);
        }
    }
}
