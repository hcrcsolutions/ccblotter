package com.fmr.ec3.oscc.state.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NodeNotFoundException extends RuntimeException {

    private final String nodeId;

    public NodeNotFoundException(String nodeId) {
        super("Node not found: " + nodeId);
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
