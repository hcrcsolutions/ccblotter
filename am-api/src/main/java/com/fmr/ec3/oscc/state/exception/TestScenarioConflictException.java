package com.fmr.ec3.oscc.state.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.CONFLICT)
public class TestScenarioConflictException extends RuntimeException {

    private final UUID scenarioId;

    public TestScenarioConflictException(UUID scenarioId, String message) {
        super(message);
        this.scenarioId = scenarioId;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }
}
