package com.example.agentmonitor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRecordingException extends RuntimeException {

    public InvalidRecordingException(String message) {
        super(message);
    }
}
