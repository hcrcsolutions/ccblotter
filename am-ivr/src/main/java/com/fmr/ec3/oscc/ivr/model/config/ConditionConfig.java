package com.fmr.ec3.oscc.ivr.model.config;

/**
 * Configuration for a CONDITION node. Built via {@link #builder()}.
 */
public class ConditionConfig {

    private final String variableName;
    private final Operator operator;
    private final String value;

    private ConditionConfig(Builder builder) {
        this.variableName = builder.variableName;
        this.operator = builder.operator;
        this.value = builder.value;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getVariableName() {
        return variableName;
    }

    public Operator getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    public enum Operator {
        EQ,
        NEQ,
        GT,
        LT,
        GTE,
        LTE,
        CONTAINS,
        MATCHES
    }

    public static final class Builder {

        private String variableName;
        private Operator operator;
        private String value;

        private Builder() {
        }

        public Builder variableName(String variableName) {
            this.variableName = variableName;
            return this;
        }

        public Builder operator(Operator operator) {
            this.operator = operator;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public ConditionConfig build() {
            if (variableName == null || variableName.isBlank()) {
                throw new IllegalArgumentException("variableName is required");
            }
            if (operator == null) {
                throw new IllegalArgumentException("operator is required");
            }
            return new ConditionConfig(this);
        }
    }
}
