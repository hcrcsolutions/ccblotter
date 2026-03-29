package com.fmr.ec3.oscc.state.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TestScenarioNotFoundException extends RuntimeException {

    private final UUID scenarioId;

    public TestScenarioNotFoundException(UUID scenarioId) {
        super("Test scenario not found: " + scenarioId);
        this.scenarioId = scenarioId;
    }

    public UUID getScenarioId() {
        return scenarioId;
    }
}
