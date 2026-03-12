package com.fmr.ec3.oscc.ivr.server.exception;

public class FlowNotFoundException extends RuntimeException {

    private final String flowId;

    public FlowNotFoundException(String flowId) {
        super("Flow not found: " + flowId);
        this.flowId = flowId;
    }

    public String getFlowId() {
        return flowId;
    }
}
