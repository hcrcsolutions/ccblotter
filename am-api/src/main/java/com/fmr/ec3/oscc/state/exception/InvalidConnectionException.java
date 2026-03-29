package com.fmr.ec3.oscc.state.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidConnectionException extends RuntimeException {

    public InvalidConnectionException(String message) {
        super(message);
    }

    public static InvalidConnectionException nodeNotHealthy(String nodeId) {
        return new InvalidConnectionException("Node " + nodeId + " is not healthy");
    }

    public static InvalidConnectionException edgeAlreadyExists(String sourceId, String targetId) {
        return new InvalidConnectionException(
                "Connection already exists: " + sourceId + " -> " + targetId);
    }

    public static InvalidConnectionException edgeNotFound(String sourceId, String targetId) {
        return new InvalidConnectionException(
                "Connection not found: " + sourceId + " -> " + targetId);
    }
}
