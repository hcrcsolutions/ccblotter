package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.entity.EdgeEntity;
import com.fmr.ec3.oscc.state.entity.NodeEntity;
import com.fmr.ec3.oscc.state.model.*;
import com.fmr.ec3.oscc.state.repository.EdgeRepository;
import com.fmr.ec3.oscc.state.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for infrastructure topology data.
 *
 * This service provides the infrastructure topology by reading:
 * - Static topology (nodes, edges) from PostgreSQL
 * - Dynamic state (sessions, metrics, trends) from Redis
 *
 * Topology changes are broadcast via WebSocket to connected clients.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InfrastructureService {

    private final SimpMessagingTemplate messagingTemplate;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final RedisStateService redisStateService;

    /**
     * Get current infrastructure topology.
     * Combines static data from PostgreSQL with dynamic data from Redis.
     */
    @Transactional(readOnly = true)
    public InfrastructureTopology getTopology() {
        List<NodeEntity> nodeEntities = nodeRepository.findAllWithDatacenter();
        List<EdgeEntity> edgeEntities = edgeRepository.findAllWithNodes();

        List<InfrastructureNode> nodes = nodeEntities.stream()
                .map(this::toInfrastructureNode)
                .collect(Collectors.toList());

        List<InfrastructureEdge> edges = edgeEntities.stream()
                .map(this::toInfrastructureEdge)
                .collect(Collectors.toList());

        return InfrastructureTopology.builder()
                .nodes(nodes)
                .edges(edges)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Broadcast infrastructure updates via WebSocket.
     * Called periodically and on topology changes.
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastTopology() {
        try {
            InfrastructureTopology topology = getTopology();
            messagingTemplate.convertAndSend("/topic/infrastructure", topology);
            log.debug("Broadcast topology with {} nodes and {} edges",
                    topology.getNodes().size(),
                    topology.getEdges().size());
        } catch (Exception e) {
            log.error("Failed to broadcast topology", e);
        }
    }

    /**
     * Convert NodeEntity to InfrastructureNode, enriching with Redis data.
     */
    private InfrastructureNode toInfrastructureNode(NodeEntity entity) {
        String nodeId = entity.getId();

        // Get dynamic data from Redis
        Integer activeSessions = redisStateService.getActiveSessions(nodeId);
        NodeMetrics metrics = redisStateService.getNodeMetrics(nodeId);
        SessionBreakdown sessionBreakdown = redisStateService.getSessionBreakdown(nodeId);
        List<TrendDataPoint> trendHistory = redisStateService.getTrendHistory(nodeId);
        Map<String, String> gateways = redisStateService.getGatewayStatuses(nodeId);
        Map<String, Integer> codecUsage = redisStateService.getCodecUsage(nodeId);

        return InfrastructureNode.builder()
                .id(entity.getId())
                .type(entity.getType())
                .hostname(entity.getHostname())
                .ipAddress(entity.getIpAddress())
                .startTime(entity.getRegisteredAt())
                .activeSessions(activeSessions != null ? activeSessions : 0)
                .maxSessions(entity.getMaxSessions())
                .healthStatus(entity.getHealthStatus())
                .datacenter(entity.getDatacenter().getId())
                .region(entity.getDatacenter().getRegion())
                .metrics(metrics)
                .sessionBreakdown(sessionBreakdown)
                .trendHistory(trendHistory)
                .carrierName(entity.getCarrierName())
                .trunkGroup(entity.getTrunkGroup())
                .gateways(gateways.isEmpty() ? null : gateways)
                .codecUsage(codecUsage.isEmpty() ? null : codecUsage)
                .maintenanceMode(entity.isMaintenanceMode())
                .build();
    }

    /**
     * Convert EdgeEntity to InfrastructureEdge.
     */
    private InfrastructureEdge toInfrastructureEdge(EdgeEntity entity) {
        return InfrastructureEdge.builder()
                .id(entity.getId())
                .sourceId(entity.getSource().getId())
                .targetId(entity.getTarget().getId())
                .bandwidthMbps(entity.getBandwidthMbps())
                .build();
    }
}
