package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a SET_VARIABLE node. Built via {@link #builder()}.
 */
public class SetVariableConfig {

    private final String variableName;
    private final String expression;

    private SetVariableConfig(Builder builder) {
        this.variableName = builder.variableName;
        this.expression = builder.expression;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getVariableName() {
        return variableName;
    }

    public String getExpression() {
        return expression;
    }

    public static final class Builder {

        private String variableName;
        private String expression;

        private Builder() {
        }

        public Builder variableName(String variableName) {
            this.variableName = variableName;
            return this;
        }

        public Builder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public SetVariableConfig build() {
            if (variableName == null || variableName.isBlank()) {
                throw new IllegalArgumentException("variableName is required");
            }
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("expression is required");
            }
            return new SetVariableConfig(this);
        }
    }
}
