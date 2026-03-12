package com.fmr.ec3.oscc.ivr.execution;

/**
 * Immutable request to execute a dynamic IVR node. Built via {@link #builder()}.
 */
public class ExecutionRequest {

    private final String flowId;
    private final String nodeId;
    private final SessionContext sessionContext;

    private ExecutionRequest(Builder builder) {
        this.flowId = builder.flowId;
        this.nodeId = builder.nodeId;
        this.sessionContext = builder.sessionContext;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFlowId() {
        return flowId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public SessionContext getSessionContext() {
        return sessionContext;
    }

    public static final class Builder {

        private String flowId;
        private String nodeId;
        private SessionContext sessionContext;

        private Builder() {
        }

        public Builder flowId(String flowId) {
            this.flowId = flowId;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder sessionContext(SessionContext sessionContext) {
            this.sessionContext = sessionContext;
            return this;
        }

        public ExecutionRequest build() {
            if (flowId == null || flowId.isBlank()) {
                throw new IllegalArgumentException("flowId is required");
            }
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId is required");
            }
            if (sessionContext == null) {
                throw new IllegalArgumentException("sessionContext is required");
            }
            return new ExecutionRequest(this);
        }
    }
}
