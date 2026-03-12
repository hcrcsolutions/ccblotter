package com.fmr.ec3.oscc.ivr.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable instruction from the IVR service to the routing server.
 * Built via {@link #builder()}.
 */
public class FlowInstruction {

    private final InstructionType type;
    private final Map<String, Object> parameters;

    private FlowInstruction(Builder builder) {
        this.type = builder.type;
        this.parameters = builder.parameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
    }

    public static Builder builder() {
        return new Builder();
    }

    public InstructionType getType() {
        return type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public static final class Builder {

        private InstructionType type;
        private Map<String, Object> parameters;

        private Builder() {
        }

        public Builder type(InstructionType type) {
            this.type = type;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public FlowInstruction build() {
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            return new FlowInstruction(this);
        }
    }
}
