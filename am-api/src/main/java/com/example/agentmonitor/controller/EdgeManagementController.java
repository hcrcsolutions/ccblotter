package com.example.agentmonitor.controller;

import com.example.agentmonitor.dto.request.AddConnectionRequest;
import com.example.agentmonitor.dto.request.BulkConnectionRequest;
import com.example.agentmonitor.dto.response.BulkConnectionResponse;
import com.example.agentmonitor.dto.response.ConnectionListResponse;
import com.example.agentmonitor.dto.response.ConnectionResponse;
import com.example.agentmonitor.service.EdgeManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/nodes/{sourceId}/connections")
@RequiredArgsConstructor
@Slf4j
@Validated
public class EdgeManagementController {

    private final EdgeManagementService edgeService;

    /**
     * Add a single connection from source to target.
     */
    @PostMapping
    public ResponseEntity<ConnectionResponse> addConnection(
            @PathVariable String sourceId,
            @Valid @RequestBody AddConnectionRequest request) {

        log.info("Add connection: {} -> {}", sourceId, request.getTargetId());
        ConnectionResponse response = edgeService.addConnection(sourceId, request.getTargetId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Remove a single connection from source to target.
     */
    @DeleteMapping("/{targetId}")
    public ResponseEntity<Void> removeConnection(
            @PathVariable String sourceId,
            @PathVariable String targetId) {

        log.info("Remove connection: {} -> {}", sourceId, targetId);
        edgeService.removeConnection(sourceId, targetId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Add multiple connections in bulk.
     */
    @PutMapping("/bulk")
    public ResponseEntity<BulkConnectionResponse> addConnectionsBulk(
            @PathVariable String sourceId,
            @Valid @RequestBody BulkConnectionRequest request) {

        log.info("Bulk add connections: {} -> {} targets", sourceId, request.getTargetIds().size());
        BulkConnectionResponse response = edgeService.addConnectionsBulk(sourceId, request.getTargetIds());
        return ResponseEntity.ok(response);
    }

    /**
     * Remove multiple connections in bulk.
     */
    @DeleteMapping("/bulk")
    public ResponseEntity<BulkConnectionResponse> removeConnectionsBulk(
            @PathVariable String sourceId,
            @Valid @RequestBody BulkConnectionRequest request) {

        log.info("Bulk remove connections: {} -> {} targets", sourceId, request.getTargetIds().size());
        BulkConnectionResponse response = edgeService.removeConnectionsBulk(sourceId, request.getTargetIds());
        return ResponseEntity.ok(response);
    }

    /**
     * Replace all connections (set).
     */
    @PutMapping
    public ResponseEntity<BulkConnectionResponse> replaceConnections(
            @PathVariable String sourceId,
            @Valid @RequestBody BulkConnectionRequest request) {

        log.info("Replace all connections: {} -> {} targets", sourceId, request.getTargetIds().size());
        BulkConnectionResponse response = edgeService.replaceConnections(sourceId, request.getTargetIds());
        return ResponseEntity.ok(response);
    }

    /**
     * Get current connections for a node.
     */
    @GetMapping
    public ResponseEntity<ConnectionListResponse> getConnections(@PathVariable String sourceId) {
        ConnectionListResponse response = edgeService.getConnections(sourceId);
        return ResponseEntity.ok(response);
    }
}
