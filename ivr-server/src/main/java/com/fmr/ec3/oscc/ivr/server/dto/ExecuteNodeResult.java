package com.fmr.ec3.oscc.ivr.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecuteNodeResult {

    private final Map<String, Object> result;
    private final Map<String, Object> updatedVariables;
    private final String instructionType;
    private final Map<String, Object> instructionParameters;

    private ExecuteNodeResult(Builder builder) {
        this.result = builder.result;
        this.updatedVariables = builder.updatedVariables;
        this.instructionType = builder.instructionType;
        this.instructionParameters = builder.instructionParameters;
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

    public String getInstructionType() {
        return instructionType;
    }

    public Map<String, Object> getInstructionParameters() {
        return instructionParameters;
    }

    public static final class Builder {

        private Map<String, Object> result;
        private Map<String, Object> updatedVariables;
        private String instructionType;
        private Map<String, Object> instructionParameters;

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

        public Builder instructionType(String instructionType) {
            this.instructionType = instructionType;
            return this;
        }

        public Builder instructionParameters(Map<String, Object> instructionParameters) {
            this.instructionParameters = instructionParameters;
            return this;
        }

        public ExecuteNodeResult build() {
            return new ExecuteNodeResult(this);
        }
    }
}
