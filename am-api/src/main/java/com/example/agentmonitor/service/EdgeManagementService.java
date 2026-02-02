package com.example.agentmonitor.service;

import com.example.agentmonitor.dto.response.BulkConnectionResponse;
import com.example.agentmonitor.dto.response.ConnectionListResponse;
import com.example.agentmonitor.dto.response.ConnectionResponse;
import com.example.agentmonitor.entity.EdgeEntity;
import com.example.agentmonitor.entity.NodeEntity;
import com.example.agentmonitor.exception.InvalidConnectionException;
import com.example.agentmonitor.exception.NodeNotFoundException;
import com.example.agentmonitor.model.ServerHealthStatus;
import com.example.agentmonitor.repository.EdgeRepository;
import com.example.agentmonitor.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EdgeManagementService {

    private final EdgeRepository edgeRepository;
    private final NodeRepository nodeRepository;
    private final RedisStateService redisStateService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ConnectionResponse addConnection(String sourceId, String targetId) {
        NodeEntity source = nodeRepository.findById(sourceId)
                .orElseThrow(() -> new NodeNotFoundException(sourceId));

        NodeEntity target = nodeRepository.findById(targetId)
                .orElseThrow(() -> new NodeNotFoundException(targetId));

        // Check if target is healthy
        if (target.getHealthStatus() != ServerHealthStatus.HEALTHY) {
            throw InvalidConnectionException.nodeNotHealthy(targetId);
        }

        // Check if edge already exists
        String edgeId = EdgeEntity.generateId(sourceId, targetId);
        if (edgeRepository.existsById(edgeId)) {
            throw InvalidConnectionException.edgeAlreadyExists(sourceId, targetId);
        }

        Instant now = Instant.now();
        EdgeEntity edge = EdgeEntity.builder()
                .id(edgeId)
                .source(source)
                .target(target)
                .createdBy(sourceId)
                .createdAt(now)
                .build();

        edgeRepository.save(edge);
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("EDGE_ADDED", sourceId, targetId);

        log.info("Connection added: {} -> {}", sourceId, targetId);

        return ConnectionResponse.builder()
                .edgeId(edgeId)
                .sourceId(sourceId)
                .targetId(targetId)
                .createdAt(now)
                .build();
    }

    @Transactional
    public void removeConnection(String sourceId, String targetId) {
        // Verify source exists
        if (!nodeRepository.existsById(sourceId)) {
            throw new NodeNotFoundException(sourceId);
        }

        String edgeId = EdgeEntity.generateId(sourceId, targetId);
        if (!edgeRepository.existsById(edgeId)) {
            throw InvalidConnectionException.edgeNotFound(sourceId, targetId);
        }

        edgeRepository.deleteById(edgeId);
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("EDGE_REMOVED", sourceId, targetId);

        log.info("Connection removed: {} -> {}", sourceId, targetId);
    }

    @Transactional
    public BulkConnectionResponse addConnectionsBulk(String sourceId, List<String> targetIds) {
        NodeEntity source = nodeRepository.findById(sourceId)
                .orElseThrow(() -> new NodeNotFoundException(sourceId));

        List<String> added = new ArrayList<>();
        List<BulkConnectionResponse.FailedConnection> failed = new ArrayList<>();
        Instant now = Instant.now();

        for (String targetId : targetIds) {
            try {
                Optional<NodeEntity> targetOpt = nodeRepository.findById(targetId);
                if (targetOpt.isEmpty()) {
                    failed.add(BulkConnectionResponse.FailedConnection.builder()
                            .targetId(targetId)
                            .reason("Node not found")
                            .build());
                    continue;
                }

                NodeEntity target = targetOpt.get();
                if (target.getHealthStatus() != ServerHealthStatus.HEALTHY) {
                    failed.add(BulkConnectionResponse.FailedConnection.builder()
                            .targetId(targetId)
                            .reason("Node is " + target.getHealthStatus())
                            .build());
                    continue;
                }

                String edgeId = EdgeEntity.generateId(sourceId, targetId);
                if (edgeRepository.existsById(edgeId)) {
                    // Already exists, skip but don't report as failure
                    added.add(targetId);
                    continue;
                }

                EdgeEntity edge = EdgeEntity.builder()
                        .id(edgeId)
                        .source(source)
                        .target(target)
                        .createdBy(sourceId)
                        .createdAt(now)
                        .build();

                edgeRepository.save(edge);
                added.add(targetId);

            } catch (Exception e) {
                log.warn("Failed to add connection {} -> {}: {}", sourceId, targetId, e.getMessage());
                failed.add(BulkConnectionResponse.FailedConnection.builder()
                        .targetId(targetId)
                        .reason(e.getMessage())
                        .build());
            }
        }

        if (!added.isEmpty()) {
            redisStateService.updateTopologyVersion();
            broadcastTopologyChange("EDGES_ADDED", sourceId, null);
        }

        long totalConnections = edgeRepository.countBySourceId(sourceId);

        log.info("Bulk add connections for {}: {} added, {} failed",
                sourceId, added.size(), failed.size());

        return BulkConnectionResponse.builder()
                .sourceId(sourceId)
                .added(added)
                .failed(failed.isEmpty() ? null : failed)
                .totalConnections((int) totalConnections)
                .build();
    }

    @Transactional
    public BulkConnectionResponse removeConnectionsBulk(String sourceId, List<String> targetIds) {
        // Verify source exists
        if (!nodeRepository.existsById(sourceId)) {
            throw new NodeNotFoundException(sourceId);
        }

        List<String> removed = new ArrayList<>();

        for (String targetId : targetIds) {
            String edgeId = EdgeEntity.generateId(sourceId, targetId);
            if (edgeRepository.existsById(edgeId)) {
                edgeRepository.deleteById(edgeId);
                removed.add(targetId);
            }
        }

        if (!removed.isEmpty()) {
            redisStateService.updateTopologyVersion();
            broadcastTopologyChange("EDGES_REMOVED", sourceId, null);
        }

        long totalConnections = edgeRepository.countBySourceId(sourceId);

        log.info("Bulk remove connections for {}: {} removed", sourceId, removed.size());

        return BulkConnectionResponse.builder()
                .sourceId(sourceId)
                .removed(removed)
                .totalConnections((int) totalConnections)
                .build();
    }

    @Transactional
    public BulkConnectionResponse replaceConnections(String sourceId, List<String> targetIds) {
        NodeEntity source = nodeRepository.findById(sourceId)
                .orElseThrow(() -> new NodeNotFoundException(sourceId));

        // Get current connections
        List<EdgeEntity> currentEdges = edgeRepository.findBySourceId(sourceId);
        List<String> previousConnections = currentEdges.stream()
                .map(e -> e.getTarget().getId())
                .collect(Collectors.toList());

        // Delete all current connections
        edgeRepository.deleteBySourceId(sourceId);

        // Add new connections
        List<String> added = new ArrayList<>();
        List<BulkConnectionResponse.FailedConnection> failed = new ArrayList<>();
        Instant now = Instant.now();

        for (String targetId : targetIds) {
            try {
                Optional<NodeEntity> targetOpt = nodeRepository.findById(targetId);
                if (targetOpt.isEmpty()) {
                    failed.add(BulkConnectionResponse.FailedConnection.builder()
                            .targetId(targetId)
                            .reason("Node not found")
                            .build());
                    continue;
                }

                NodeEntity target = targetOpt.get();
                if (target.getHealthStatus() != ServerHealthStatus.HEALTHY) {
                    failed.add(BulkConnectionResponse.FailedConnection.builder()
                            .targetId(targetId)
                            .reason("Node is " + target.getHealthStatus())
                            .build());
                    continue;
                }

                String edgeId = EdgeEntity.generateId(sourceId, targetId);
                EdgeEntity edge = EdgeEntity.builder()
                        .id(edgeId)
                        .source(source)
                        .target(target)
                        .createdBy(sourceId)
                        .createdAt(now)
                        .build();

                edgeRepository.save(edge);
                added.add(targetId);

            } catch (Exception e) {
                log.warn("Failed to add connection {} -> {}: {}", sourceId, targetId, e.getMessage());
                failed.add(BulkConnectionResponse.FailedConnection.builder()
                        .targetId(targetId)
                        .reason(e.getMessage())
                        .build());
            }
        }

        redisStateService.updateTopologyVersion();
        broadcastTopologyChange("EDGES_REPLACED", sourceId, null);

        // Calculate removed connections
        Set<String> addedSet = new HashSet<>(added);
        List<String> removed = previousConnections.stream()
                .filter(id -> !addedSet.contains(id))
                .collect(Collectors.toList());

        log.info("Replace connections for {}: {} previous, {} current, {} added, {} removed",
                sourceId, previousConnections.size(), added.size(), added.size(), removed.size());

        return BulkConnectionResponse.builder()
                .sourceId(sourceId)
                .previousConnections(previousConnections)
                .currentConnections(added)
                .added(added)
                .removed(removed)
                .failed(failed.isEmpty() ? null : failed)
                .totalConnections(added.size())
                .build();
    }

    @Transactional(readOnly = true)
    public ConnectionListResponse getConnections(String sourceId) {
        // Verify source exists
        if (!nodeRepository.existsById(sourceId)) {
            throw new NodeNotFoundException(sourceId);
        }

        List<EdgeEntity> edges = edgeRepository.findBySourceId(sourceId);

        List<ConnectionListResponse.ConnectionDetail> connections = edges.stream()
                .map(edge -> ConnectionListResponse.ConnectionDetail.builder()
                        .targetId(edge.getTarget().getId())
                        .targetHostname(edge.getTarget().getHostname())
                        .targetStatus(edge.getTarget().getHealthStatus())
                        .connectedAt(edge.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ConnectionListResponse.builder()
                .sourceId(sourceId)
                .connections(connections)
                .total(connections.size())
                .build();
    }

    private void broadcastTopologyChange(String eventType, String sourceId, String targetId) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("sourceId", sourceId);
        if (targetId != null) {
            event.put("targetId", targetId);
        }
        event.put("timestamp", Instant.now().toString());
        messagingTemplate.convertAndSend("/topic/topology/changes", (Object) event);
    }
}
