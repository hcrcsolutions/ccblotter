package com.example.agentmonitor.controller;

import com.example.agentmonitor.entity.EdgeEntity;
import com.example.agentmonitor.entity.NodeEntity;
import com.example.agentmonitor.model.InfraServerType;
import com.example.agentmonitor.repository.EdgeRepository;
import com.example.agentmonitor.repository.NodeRepository;
import com.example.agentmonitor.service.RedisStateService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/edges")
@RequiredArgsConstructor
@Slf4j
@Validated
public class EdgeController {

    private final EdgeRepository edgeRepository;
    private final NodeRepository nodeRepository;
    private final RedisStateService redisStateService;
    private final SimpMessagingTemplate messagingTemplate;

    // Valid topology: TRUNK -> SBC -> SIP -> MEDIA
    private static final Map<InfraServerType, InfraServerType> VALID_EDGE_TARGETS = Map.of(
            InfraServerType.TRUNK, InfraServerType.SBC,
            InfraServerType.SBC, InfraServerType.SIP,
            InfraServerType.SIP, InfraServerType.MEDIA
    );

    /**
     * List all edges.
     */
    @GetMapping
    public ResponseEntity<List<EdgeResponse>> listEdges() {
        List<EdgeEntity> edges = edgeRepository.findAllWithNodes();
        List<EdgeResponse> response = edges.stream()
                .map(this::toEdgeResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Get edges by source node.
     */
    @GetMapping("/by-source/{sourceId}")
    public ResponseEntity<List<EdgeResponse>> getEdgesBySource(@PathVariable String sourceId) {
        List<EdgeEntity> edges = edgeRepository.findBySourceId(sourceId);
        List<EdgeResponse> response = edges.stream()
                .map(this::toEdgeResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Get edges by target node.
     */
    @GetMapping("/by-target/{targetId}")
    public ResponseEntity<List<EdgeResponse>> getEdgesByTarget(@PathVariable String targetId) {
        List<EdgeEntity> edges = edgeRepository.findByTargetId(targetId);
        List<EdgeResponse> response = edges.stream()
                .map(this::toEdgeResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new edge.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> createEdge(@Valid @RequestBody EdgeRequest request) {
        // Validate source exists
        NodeEntity source = nodeRepository.findById(request.sourceId()).orElse(null);
        if (source == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "SOURCE_NOT_FOUND", "message", "Source node not found: " + request.sourceId()));
        }

        // Validate target exists
        NodeEntity target = nodeRepository.findById(request.targetId()).orElse(null);
        if (target == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "TARGET_NOT_FOUND", "message", "Target node not found: " + request.targetId()));
        }

        // Validate topology: only allow valid edge types
        InfraServerType expectedTargetType = VALID_EDGE_TARGETS.get(source.getType());
        if (expectedTargetType == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_SOURCE_TYPE", "message",
                            source.getType() + " nodes cannot be edge sources"));
        }
        if (target.getType() != expectedTargetType) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_EDGE_TYPE", "message",
                            "Invalid edge: " + source.getType() + " can only connect to " + expectedTargetType +
                                    ", not " + target.getType()));
        }

        // Check if edge already exists (JPA save() with existing ID does UPDATE, not INSERT)
        String edgeId = EdgeEntity.generateId(request.sourceId(), request.targetId());
        if (edgeRepository.existsById(edgeId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "EDGE_EXISTS", "message",
                            "Edge already exists between " + request.sourceId() + " and " + request.targetId()));
        }

        // Create edge
        Instant now = Instant.now();
        EdgeEntity edge = EdgeEntity.builder()
                .id(edgeId)
                .source(source)
                .target(target)
                .bandwidthMbps(request.bandwidthMbps())
                .createdBy("editor")
                .createdAt(now)
                .build();

        edgeRepository.save(edge);

        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("EDGE_ADDED", request.sourceId(), request.targetId());

        log.info("Created edge: {} -> {}", request.sourceId(), request.targetId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toEdgeResponse(edge));
    }

    /**
     * Delete an edge.
     */
    @DeleteMapping("/{edgeId}")
    @Transactional
    public ResponseEntity<?> deleteEdge(@PathVariable String edgeId) {
        // Use fetch-join to eagerly load source/target for broadcast
        EdgeEntity edge = edgeRepository.findByIdWithNodes(edgeId).orElse(null);
        if (edge == null) {
            return ResponseEntity.notFound().build();
        }

        String sourceId = edge.getSource().getId();
        String targetId = edge.getTarget().getId();

        edgeRepository.deleteById(edgeId);
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("EDGE_REMOVED", sourceId, targetId);

        log.info("Deleted edge: {}", edgeId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Delete edge by source and target IDs.
     */
    @DeleteMapping("/by-nodes")
    @Transactional
    public ResponseEntity<?> deleteEdgeByNodes(
            @RequestParam String sourceId,
            @RequestParam String targetId) {
        String edgeId = EdgeEntity.generateId(sourceId, targetId);
        if (!edgeRepository.existsById(edgeId)) {
            return ResponseEntity.notFound().build();
        }

        edgeRepository.deleteById(edgeId);
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("EDGE_REMOVED", sourceId, targetId);

        log.info("Deleted edge: {} -> {}", sourceId, targetId);

        return ResponseEntity.noContent().build();
    }

    private EdgeResponse toEdgeResponse(EdgeEntity edge) {
        Objects.requireNonNull(edge.getSource(), "Edge source cannot be null");
        Objects.requireNonNull(edge.getTarget(), "Edge target cannot be null");
        Objects.requireNonNull(edge.getCreatedAt(), "Edge createdAt cannot be null");

        return new EdgeResponse(
                edge.getId(),
                edge.getSource().getId(),
                edge.getSource().getType().name(),
                edge.getSource().getHostname(),
                edge.getTarget().getId(),
                edge.getTarget().getType().name(),
                edge.getTarget().getHostname(),
                edge.getBandwidthMbps(),
                edge.getCreatedAt().toString()
        );
    }

    private void broadcastTopologyChange(String eventType, String sourceId, String targetId) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("sourceId", sourceId);
        event.put("targetId", targetId);
        event.put("timestamp", Instant.now().toString());
        messagingTemplate.convertAndSend("/topic/topology/changes", (Object) event);
    }

    /**
     * Request DTO for creating edges.
     */
    public record EdgeRequest(
            @NotBlank(message = "Source ID is required") String sourceId,
            @NotBlank(message = "Target ID is required") String targetId,
            @Min(value = 0, message = "Bandwidth must be non-negative") Integer bandwidthMbps
    ) {}

    /**
     * Response DTO for edges.
     */
    public record EdgeResponse(
            String id,
            String sourceId,
            String sourceType,
            String sourceHostname,
            String targetId,
            String targetType,
            String targetHostname,
            Integer bandwidthMbps,
            String createdAt
    ) {}
}
