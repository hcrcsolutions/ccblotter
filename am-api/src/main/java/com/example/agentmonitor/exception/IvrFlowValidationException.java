package com.example.agentmonitor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IvrFlowValidationException extends RuntimeException {

    private final List<Map<String, Object>> validationIssues;

    public IvrFlowValidationException(String message, List<Map<String, Object>> validationIssues) {
        super(message);
        this.validationIssues = validationIssues;
    }

    public List<Map<String, Object>> getValidationIssues() {
        return validationIssues;
    }
}
