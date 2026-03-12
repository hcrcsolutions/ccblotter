package com.example.agentmonitor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class IvrFlowConflictException extends RuntimeException {

    private final UUID flowId;

    public IvrFlowConflictException(UUID flowId, String message) {
        super(message);
        this.flowId = flowId;
    }

    public UUID getFlowId() {
        return flowId;
    }
}
