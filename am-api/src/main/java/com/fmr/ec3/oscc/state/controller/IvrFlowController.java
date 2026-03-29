package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.grid.GridRequest;
import com.fmr.ec3.oscc.state.dto.grid.GridResponse;
import com.fmr.ec3.oscc.state.dto.request.CreateFlowRequest;
import com.fmr.ec3.oscc.state.dto.request.SaveFlowContentRequest;
import com.fmr.ec3.oscc.state.dto.request.UpdateFlowRequest;
import com.fmr.ec3.oscc.state.dto.response.*;
import com.fmr.ec3.oscc.state.service.GridFilterService;
import com.fmr.ec3.oscc.state.service.IvrFlowService;
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
@RequestMapping("/api/v1/ivr/flows")
@RequiredArgsConstructor
@Slf4j
@Validated
public class IvrFlowController {

    private final IvrFlowService flowService;
    private final GridFilterService gridFilterService;

    private static final Map<String, Function<FlowSummaryDto, Comparable<?>>> FIELD_ACCESSORS =
            new HashMap<>();

    static {
        FIELD_ACCESSORS.put("name", FlowSummaryDto::getName);
        FIELD_ACCESSORS.put("description", FlowSummaryDto::getDescription);
        FIELD_ACCESSORS.put("businessUnit", FlowSummaryDto::getBusinessUnit);
        FIELD_ACCESSORS.put("status", FlowSummaryDto::getStatus);
        FIELD_ACCESSORS.put("version", row -> row.getVersion());
        FIELD_ACCESSORS.put("updatedAt", FlowSummaryDto::getUpdatedAt);
    }

    /**
     * Query IVR flows with ag-grid filtering, sorting, and pagination.
     */
    @PostMapping("/query")
    public ResponseEntity<GridResponse<FlowSummaryDto>> queryFlows(
            @RequestBody GridRequest request) {
        List<FlowSummaryDto> allFlows = flowService.listFlows();
        GridResponse<FlowSummaryDto> response = gridFilterService.applyFilterSortPage(
                allFlows, request, FIELD_ACCESSORS, Map.of());
        response.setMetadata(Map.of("flowCount", (long) allFlows.size()));
        return ResponseEntity.ok(response);
    }

    /**
     * List all IVR flows ordered by last updated.
     */
    @GetMapping
    public ResponseEntity<List<FlowSummaryDto>> listFlows() {
        return ResponseEntity.ok(flowService.listFlows());
    }

    /**
     * Create a new IVR flow.
     */
    @PostMapping
    public ResponseEntity<FlowDetailDto> createFlow(@Valid @RequestBody CreateFlowRequest request) {
        log.info("Creating IVR flow: {}", request.getName());
        FlowDetailDto flow = flowService.createFlow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(flow);
    }

    /**
     * Get IVR flow details by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlowDetailDto> getFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(flowService.getFlow(id));
    }

    /**
     * Update an IVR flow.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlowDetailDto> updateFlow(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFlowRequest request) {
        return ResponseEntity.ok(flowService.updateFlow(id, request));
    }

    /**
     * Delete an IVR flow.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlow(@PathVariable UUID id) {
        flowService.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get the latest flow content (nodes, edges, variables).
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<FlowContentDto> getContent(@PathVariable UUID id) {
        return ResponseEntity.ok(flowService.getContent(id));
    }

    /**
     * Save flow content (creates a new version).
     */
    @PutMapping("/{id}/content")
    public ResponseEntity<FlowContentDto> saveContent(
            @PathVariable UUID id,
            @Valid @RequestBody SaveFlowContentRequest request) {
        return ResponseEntity.ok(flowService.saveContent(id, request));
    }

    /**
     * Publish a flow (validates and changes status to PUBLISHED).
     */
    @PostMapping("/{id}/publish")
    public ResponseEntity<PublishResultDto> publishFlow(@PathVariable UUID id) {
        PublishResultDto result = flowService.publishFlow(id);
        HttpStatus status = result.isPublished() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Get the published version of a flow.
     */
    @GetMapping("/{id}/published")
    public ResponseEntity<FlowDetailDto> getPublishedFlow(@PathVariable UUID id) {
        return ResponseEntity.ok(flowService.getPublishedFlow(id));
    }

    /**
     * Get all content versions for a flow.
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<FlowVersionDto>> getVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(flowService.getVersions(id));
    }
}
