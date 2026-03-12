package com.fmr.ec3.oscc.ivr.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable result of dynamic node execution. Built via {@link #builder()}.
 */
public class ExecutionResult {

    private final Map<String, Object> result;
    private final Map<String, Object> updatedVariables;
    private final FlowInstruction instruction;

    private ExecutionResult(Builder builder) {
        this.result = builder.result == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.result));
        this.updatedVariables = builder.updatedVariables == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.updatedVariables));
        this.instruction = builder.instruction;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public Map<String, Object> getUpdatedVariables() {
        return updatedVariables;
    }

    public FlowInstruction getInstruction() {
        return instruction;
    }

    public static final class Builder {

        private Map<String, Object> result;
        private Map<String, Object> updatedVariables;
        private FlowInstruction instruction;

        private Builder() {
        }

        public Builder result(Map<String, Object> result) {
            this.result = result;
            return this;
        }

        public Builder updatedVariables(Map<String, Object> updatedVariables) {
            this.updatedVariables = updatedVariables;
            return this;
        }

        public Builder instruction(FlowInstruction instruction) {
            this.instruction = instruction;
            return this;
        }

        public ExecutionResult build() {
            return new ExecutionResult(this);
        }
    }
}
