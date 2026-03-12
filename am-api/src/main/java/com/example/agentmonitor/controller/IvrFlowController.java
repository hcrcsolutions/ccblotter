package com.example.agentmonitor.controller;

import com.example.agentmonitor.dto.request.CreateFlowRequest;
import com.example.agentmonitor.dto.request.SaveFlowContentRequest;
import com.example.agentmonitor.dto.request.UpdateFlowRequest;
import com.example.agentmonitor.dto.response.*;
import com.example.agentmonitor.service.IvrFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ivr/flows")
@RequiredArgsConstructor
@Slf4j
@Validated
public class IvrFlowController {

    private final IvrFlowService flowService;

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
