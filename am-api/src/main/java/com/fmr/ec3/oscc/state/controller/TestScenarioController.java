package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.grid.GridRequest;
import com.fmr.ec3.oscc.state.dto.grid.GridResponse;
import com.fmr.ec3.oscc.state.dto.request.CreateScenarioRequest;
import com.fmr.ec3.oscc.state.dto.request.RunScenarioRequest;
import com.fmr.ec3.oscc.state.dto.request.SaveScenarioContentRequest;
import com.fmr.ec3.oscc.state.dto.request.UpdateScenarioRequest;
import com.fmr.ec3.oscc.state.dto.response.*;
import com.fmr.ec3.oscc.state.service.GridFilterService;
import com.fmr.ec3.oscc.state.service.ScenarioExecutionService;
import com.fmr.ec3.oscc.state.service.TestScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/test-scenarios")
@RequiredArgsConstructor
@Slf4j
@Validated
public class TestScenarioController {

    private final TestScenarioService scenarioService;
    private final ScenarioExecutionService executionService;
    private final GridFilterService gridFilterService;

    private static final Map<String, Function<ScenarioSummaryDto, Comparable<?>>> FIELD_ACCESSORS =
            new HashMap<>();

    static {
        FIELD_ACCESSORS.put("name", ScenarioSummaryDto::getName);
        FIELD_ACCESSORS.put("description", ScenarioSummaryDto::getDescription);
        FIELD_ACCESSORS.put("status", ScenarioSummaryDto::getStatus);
        FIELD_ACCESSORS.put("version", row -> row.getVersion());
        FIELD_ACCESSORS.put("lastRunResult", ScenarioSummaryDto::getLastRunResult);
        FIELD_ACCESSORS.put("lastRunAt", ScenarioSummaryDto::getLastRunAt);
        FIELD_ACCESSORS.put("updatedAt", ScenarioSummaryDto::getUpdatedAt);
    }

    /**
     * Query test scenarios with ag-grid filtering, sorting, and pagination.
     */
    @PostMapping("/query")
    public ResponseEntity<GridResponse<ScenarioSummaryDto>> queryScenarios(
            @RequestBody GridRequest request) {
        List<ScenarioSummaryDto> all = scenarioService.listScenarios();
        GridResponse<ScenarioSummaryDto> response = gridFilterService.applyFilterSortPage(
                all, request, FIELD_ACCESSORS, Map.of());
        response.setMetadata(Map.of("scenarioCount", (long) all.size()));
        return ResponseEntity.ok(response);
    }

    /**
     * List all test scenarios ordered by last updated.
     */
    @GetMapping
    public ResponseEntity<List<ScenarioSummaryDto>> listScenarios() {
        return ResponseEntity.ok(scenarioService.listScenarios());
    }

    /**
     * Create a new test scenario.
     */
    @PostMapping
    public ResponseEntity<ScenarioDetailDto> createScenario(
            @Valid @RequestBody CreateScenarioRequest request) {
        log.info("Creating test scenario: {}", request.getName());
        ScenarioDetailDto scenario = scenarioService.createScenario(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(scenario);
    }

    /**
     * Get test scenario details by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ScenarioDetailDto> getScenario(@PathVariable UUID id) {
        return ResponseEntity.ok(scenarioService.getScenario(id));
    }

    /**
     * Update a test scenario.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ScenarioDetailDto> updateScenario(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScenarioRequest request) {
        return ResponseEntity.ok(scenarioService.updateScenario(id, request));
    }

    /**
     * Delete a test scenario.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScenario(@PathVariable UUID id) {
        scenarioService.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get the latest scenario content.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<ScenarioContentDto> getContent(@PathVariable UUID id) {
        return ResponseEntity.ok(scenarioService.getContent(id));
    }

    /**
     * Save scenario content (creates a new version).
     */
    @PutMapping("/{id}/content")
    public ResponseEntity<ScenarioContentDto> saveContent(
            @PathVariable UUID id,
            @Valid @RequestBody SaveScenarioContentRequest request) {
        return ResponseEntity.ok(scenarioService.saveContent(id, request));
    }

    /**
     * Get all content versions for a scenario.
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<ScenarioVersionDto>> getVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(scenarioService.getVersions(id));
    }

    /**
     * List runs for a scenario.
     */
    @GetMapping("/{id}/runs")
    public ResponseEntity<List<ScenarioRunDto>> listRuns(@PathVariable UUID id) {
        return ResponseEntity.ok(scenarioService.listRuns(id));
    }

    /**
     * Get a single run by ID.
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<ScenarioRunDto> getRun(@PathVariable UUID runId) {
        return ResponseEntity.ok(scenarioService.getRun(runId));
    }

    /**
     * Execute a test scenario. Returns 202 Accepted with the run ID.
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<ScenarioRunDto> runScenario(
            @PathVariable UUID id,
            @RequestBody(required = false) RunScenarioRequest request) {
        String timingMode = request != null ? request.getTimingMode() : "COMPRESSED";
        log.info("Starting scenario execution: {} with timing mode: {}", id, timingMode);
        ScenarioRunDto run = executionService.startExecution(id, timingMode);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
    }
}
