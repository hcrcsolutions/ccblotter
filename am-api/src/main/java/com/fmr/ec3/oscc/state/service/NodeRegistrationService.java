package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.config.NodeHealthProperties;
import com.fmr.ec3.oscc.state.dto.request.HeartbeatRequest;
import com.fmr.ec3.oscc.state.dto.request.NodeRegistrationRequest;
import com.fmr.ec3.oscc.state.dto.response.HeartbeatResponse;
import com.fmr.ec3.oscc.state.dto.response.NodeQueryResponse;
import com.fmr.ec3.oscc.state.dto.response.NodeRegistrationResponse;
import com.fmr.ec3.oscc.state.dto.response.NodeSummaryDto;
import com.fmr.ec3.oscc.state.entity.DatacenterEntity;
import com.fmr.ec3.oscc.state.entity.NodeEntity;
import com.fmr.ec3.oscc.state.exception.NodeNotFoundException;
import com.fmr.ec3.oscc.state.model.InfraServerType;
import com.fmr.ec3.oscc.state.model.ServerHealthStatus;
import com.fmr.ec3.oscc.state.repository.DatacenterRepository;
import com.fmr.ec3.oscc.state.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NodeRegistrationService {

    private final NodeRepository nodeRepository;
    private final DatacenterRepository datacenterRepository;
    private final RedisStateService redisStateService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NodeHealthProperties healthProperties;

    @Transactional
    public NodeRegistrationResponse registerNode(NodeRegistrationRequest request) {
        // Check if datacenter exists, create if not
        DatacenterEntity datacenter = datacenterRepository.findById(request.getDatacenter())
                .orElseGet(() -> createDatacenter(request.getDatacenter(), request.getRegion()));

        // Check if node already exists
        Optional<NodeEntity> existingNode = nodeRepository.findById(request.getId());

        Instant now = Instant.now();
        NodeEntity node;
        boolean isReregistration = false;

        if (existingNode.isPresent()) {
            // Re-registration
            node = existingNode.get();
            node.setHostname(request.getHostname());
            node.setIpAddress(request.getIpAddress());
            node.setMaxSessions(request.getMaxSessions());
            node.setHealthStatus(ServerHealthStatus.HEALTHY);
            node.setLastHeartbeatAt(now);
            node.setUpdatedAt(now);
            node.setMetadata(request.getMetadata());
            if (request.getCarrierName() != null) {
                node.setCarrierName(request.getCarrierName());
            }
            if (request.getTrunkGroup() != null) {
                node.setTrunkGroup(request.getTrunkGroup());
            }
            isReregistration = true;

            log.info("Node re-registered: {} ({})", node.getId(), node.getType());
        } else {
            // New registration
            node = NodeEntity.builder()
                    .id(request.getId())
                    .type(request.getType())
                    .hostname(request.getHostname())
                    .ipAddress(request.getIpAddress())
                    .datacenter(datacenter)
                    .maxSessions(request.getMaxSessions())
                    .carrierName(request.getCarrierName())
                    .trunkGroup(request.getTrunkGroup())
                    .registrationSource("SELF")
                    .registeredAt(now)
                    .lastHeartbeatAt(now)
                    .healthStatus(ServerHealthStatus.HEALTHY)
                    .metadata(request.getMetadata())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            log.info("New node registered: {} ({})", node.getId(), node.getType());
        }

        nodeRepository.save(node);

        // Initialize Redis state
        redisStateService.initializeNodeState(node.getId());
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("NODE_REGISTERED", node.getId());

        return NodeRegistrationResponse.builder()
                .id(node.getId())
                .status(node.getHealthStatus())
                .registeredAt(node.getRegisteredAt())
                .reregisteredAt(isReregistration ? now : null)
                .reregistered(isReregistration)
                .heartbeatIntervalSeconds(healthProperties.getHeartbeatIntervalSeconds())
                .heartbeatTimeoutSeconds(healthProperties.getUnhealthyThresholdSeconds())
                .message(isReregistration ? "Node re-registered successfully" : null)
                .build();
    }

    @Transactional
    public HeartbeatResponse processHeartbeat(String nodeId, HeartbeatRequest request) {
        NodeEntity node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        Instant now = Instant.now();

        // Update database
        node.setLastHeartbeatAt(now);
        node.setHealthStatus(ServerHealthStatus.HEALTHY);
        node.setUpdatedAt(now);
        nodeRepository.save(node);

        // Update Redis state
        redisStateService.updateHeartbeat(nodeId, now);

        if (request.getMetrics() != null) {
            redisStateService.setNodeMetrics(nodeId, request.getMetrics());
        }

        if (request.getSessionBreakdown() != null && request.getActiveSessions() != null) {
            redisStateService.setSessionCounts(nodeId, request.getActiveSessions(), request.getSessionBreakdown());
        } else if (request.getActiveSessions() != null) {
            redisStateService.setActiveSessions(nodeId, request.getActiveSessions());
        }

        // Add trend data point
        if (request.getMetrics() != null && request.getActiveSessions() != null) {
            redisStateService.addTrendDataPoint(
                    nodeId,
                    request.getActiveSessions(),
                    request.getMetrics().getCpuPercent());
        }

        return HeartbeatResponse.builder()
                .id(nodeId)
                .status(ServerHealthStatus.HEALTHY)
                .lastHeartbeat(now)
                .nextHeartbeatDue(now.plusSeconds(healthProperties.getHeartbeatIntervalSeconds()))
                .build();
    }

    @Transactional
    public void deregisterNode(String nodeId) {
        NodeEntity node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));

        // Remove from database
        nodeRepository.delete(node);

        // Clean up Redis state
        redisStateService.removeNodeState(nodeId);
        redisStateService.updateTopologyVersion();

        // Broadcast topology change
        broadcastTopologyChange("NODE_DEREGISTERED", nodeId);

        log.info("Node deregistered: {}", nodeId);
    }

    @Transactional(readOnly = true)
    public NodeQueryResponse queryNodes(
            InfraServerType type,
            String datacenter,
            ServerHealthStatus status,
            boolean includeUnhealthy) {

        List<NodeEntity> nodes;

        // Use JOIN FETCH queries to eagerly load datacenter and prevent LazyInitializationException
        if (type != null && datacenter != null) {
            nodes = nodeRepository.findByTypeAndDatacenterIdWithDatacenter(type, datacenter);
        } else if (type != null) {
            nodes = nodeRepository.findByTypeWithDatacenter(type);
        } else if (datacenter != null) {
            nodes = nodeRepository.findByDatacenterIdWithDatacenter(datacenter);
        } else {
            nodes = nodeRepository.findAllWithDatacenter();
        }

        // Filter by status
        if (status != null) {
            nodes = nodes.stream()
                    .filter(n -> n.getHealthStatus() == status)
                    .collect(Collectors.toList());
        } else if (!includeUnhealthy) {
            nodes = nodes.stream()
                    .filter(n -> n.getHealthStatus() == ServerHealthStatus.HEALTHY)
                    .collect(Collectors.toList());
        }

        // Enrich with Redis data
        List<NodeSummaryDto> nodeSummaries = nodes.stream()
                .map(this::toNodeSummary)
                .collect(Collectors.toList());

        return NodeQueryResponse.builder()
                .nodes(nodeSummaries)
                .total(nodeSummaries.size())
                .filter(NodeQueryResponse.Filter.builder()
                        .type(type)
                        .datacenter(datacenter)
                        .status(status)
                        .build())
                .build();
    }

    private NodeSummaryDto toNodeSummary(NodeEntity node) {
        Integer activeSessions = redisStateService.getActiveSessions(node.getId());

        return NodeSummaryDto.builder()
                .id(node.getId())
                .type(node.getType())
                .hostname(node.getHostname())
                .ipAddress(node.getIpAddress())
                .datacenter(node.getDatacenter().getId())
                .region(node.getDatacenter().getRegion())
                .status(node.getHealthStatus())
                .maxSessions(node.getMaxSessions())
                .activeSessions(activeSessions != null ? activeSessions : 0)
                .availableCapacity(node.getMaxSessions() - (activeSessions != null ? activeSessions : 0))
                .lastHeartbeat(node.getLastHeartbeatAt())
                .build();
    }

    private DatacenterEntity createDatacenter(String id, String region) {
        DatacenterEntity dc = DatacenterEntity.builder()
                .id(id)
                .region(region != null ? region : "unknown")
                .displayName(id.toUpperCase())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        log.info("Auto-created datacenter: {} ({})", id, region);
        return datacenterRepository.save(dc);
    }

    private void broadcastTopologyChange(String eventType, String nodeId) {
        Map<String, Object> event = Map.of(
                "type", eventType,
                "nodeId", nodeId,
                "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/topology/changes", (Object) event);
    }
}
