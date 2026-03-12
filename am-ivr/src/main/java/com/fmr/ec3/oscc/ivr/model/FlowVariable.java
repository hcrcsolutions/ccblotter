package com.fmr.ec3.oscc.ivr.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Immutable flow variable declaration. Built via {@link #builder()}.
 */
@JsonDeserialize(builder = FlowVariable.Builder.class)
public class FlowVariable {

    private final String name;
    private final VariableType type;
    private final String defaultValue;

    private FlowVariable(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.defaultValue = builder.defaultValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public VariableType getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {

        private String name;
        private VariableType type;
        private String defaultValue;

        Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(VariableType type) {
            this.type = type;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public FlowVariable build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            return new FlowVariable(this);
        }
    }
}
