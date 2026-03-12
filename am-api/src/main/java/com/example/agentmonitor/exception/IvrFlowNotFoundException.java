package com.example.agentmonitor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class IvrFlowNotFoundException extends RuntimeException {

    private final UUID flowId;

    public IvrFlowNotFoundException(UUID flowId) {
        super("IVR flow not found: " + flowId);
        this.flowId = flowId;
    }

    public UUID getFlowId() {
        return flowId;
    }
}
