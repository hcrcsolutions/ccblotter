package com.example.agentmonitor.controller;

import com.example.agentmonitor.dto.request.HeartbeatRequest;
import com.example.agentmonitor.dto.request.NodeRegistrationRequest;
import com.example.agentmonitor.dto.response.HeartbeatResponse;
import com.example.agentmonitor.dto.response.NodeQueryResponse;
import com.example.agentmonitor.dto.response.NodeRegistrationResponse;
import com.example.agentmonitor.model.InfraServerType;
import com.example.agentmonitor.model.ServerHealthStatus;
import com.example.agentmonitor.service.NodeRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
@Slf4j
@Validated
public class NodeRegistrationController {

    private final NodeRegistrationService registrationService;

    /**
     * Register a new node or re-register an existing one.
     */
    @PostMapping("/register")
    public ResponseEntity<NodeRegistrationResponse> registerNode(
            @Valid @RequestBody NodeRegistrationRequest request) {

        log.info("Node registration request: {} ({})", request.getId(), request.getType());

        NodeRegistrationResponse response = registrationService.registerNode(request);

        HttpStatus status = response.isReregistered() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Send heartbeat for a registered node.
     */
    @PostMapping("/{nodeId}/heartbeat")
    public ResponseEntity<HeartbeatResponse> heartbeat(
            @PathVariable String nodeId,
            @Valid @RequestBody HeartbeatRequest request) {

        HeartbeatResponse response = registrationService.processHeartbeat(nodeId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deregister a node (graceful shutdown).
     */
    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deregisterNode(@PathVariable String nodeId) {
        log.info("Node deregistration request: {}", nodeId);
        registrationService.deregisterNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Query available nodes (service discovery).
     */
    @GetMapping
    public ResponseEntity<NodeQueryResponse> queryNodes(
            @RequestParam(required = false) InfraServerType type,
            @RequestParam(required = false) String datacenter,
            @RequestParam(required = false) ServerHealthStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean includeUnhealthy) {

        NodeQueryResponse response = registrationService.queryNodes(type, datacenter, status, includeUnhealthy);
        return ResponseEntity.ok(response);
    }
}
