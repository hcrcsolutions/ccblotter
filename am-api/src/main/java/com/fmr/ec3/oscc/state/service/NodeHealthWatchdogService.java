package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.config.NodeHealthProperties;
import com.fmr.ec3.oscc.state.entity.NodeEntity;
import com.fmr.ec3.oscc.state.model.ServerHealthStatus;
import com.fmr.ec3.oscc.state.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NodeHealthWatchdogService {

    private final NodeRepository nodeRepository;
    private final RedisStateService redisStateService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NodeHealthProperties healthProperties;

    /**
     * Runs every 15 seconds to check for stale nodes.
     * Uses distributed lock to ensure only one instance runs.
     */
    @Scheduled(fixedRateString = "${app.node.watchdog-interval-seconds:15}000")
    public void checkNodeHealth() {
        // Try to acquire distributed lock
        String instanceId = getInstanceId();
        boolean acquired = redisStateService.tryAcquireWatchdogLock(
                instanceId,
                Duration.ofSeconds(healthProperties.getWatchdogIntervalSeconds() + 5));

        if (acquired) {
            try {
                doHealthCheck();
            } catch (Exception e) {
                log.error("Health check failed", e);
            }
        } else {
            log.debug("Another instance is running health watchdog");
        }
    }

    @Transactional
    protected void doHealthCheck() {
        Instant unhealthyThreshold = Instant.now()
                .minusSeconds(healthProperties.getUnhealthyThresholdSeconds());

        Instant removalThreshold = Instant.now()
                .minusSeconds(healthProperties.getRemovalThresholdSeconds());

        // Find nodes to mark as unhealthy
        List<NodeEntity> staleNodes = nodeRepository.findByLastHeartbeatAtBeforeAndHealthStatus(
                unhealthyThreshold, ServerHealthStatus.HEALTHY);

        int markedUnhealthy = 0;
        for (NodeEntity node : staleNodes) {
            node.setHealthStatus(ServerHealthStatus.UNHEALTHY);
            node.setUpdatedAt(Instant.now());
            nodeRepository.save(node);
            markedUnhealthy++;

            log.warn("Node {} marked UNHEALTHY (last heartbeat: {})",
                    node.getId(), node.getLastHeartbeatAt());

            // Broadcast individual node status change
            broadcastNodeStatusChange(node.getId(), ServerHealthStatus.UNHEALTHY);
        }

        // Find nodes to remove (optional - can be disabled)
        if (healthProperties.isAutoRemoveEnabled()) {
            List<NodeEntity> deadNodes = nodeRepository.findByLastHeartbeatAtBefore(removalThreshold);

            for (NodeEntity node : deadNodes) {
                log.warn("Removing dead node {} (last heartbeat: {})",
                        node.getId(), node.getLastHeartbeatAt());

                // Clean up Redis state
                redisStateService.removeNodeState(node.getId());

                nodeRepository.delete(node);

                // Broadcast node removal
                broadcastNodeRemoval(node.getId());
            }

            if (!deadNodes.isEmpty()) {
                log.info("Removed {} dead nodes", deadNodes.size());
            }
        }

        if (markedUnhealthy > 0) {
            log.info("Marked {} nodes as UNHEALTHY", markedUnhealthy);
            // Trigger topology update broadcast
            redisStateService.updateTopologyVersion();
            broadcastTopologyChange();
        }
    }

    private String getInstanceId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }
        return "am-api-" + ProcessHandle.current().pid();
    }

    private void broadcastNodeStatusChange(String nodeId, ServerHealthStatus status) {
        Map<String, Object> event = Map.of(
                "type", "NODE_STATUS_CHANGED",
                "nodeId", nodeId,
                "status", status.name(),
                "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/topology/changes", (Object) event);
    }

    private void broadcastNodeRemoval(String nodeId) {
        Map<String, Object> event = Map.of(
                "type", "NODE_REMOVED",
                "nodeId", nodeId,
                "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/topology/changes", (Object) event);
    }

    private void broadcastTopologyChange() {
        Map<String, Object> event = Map.of(
                "type", "TOPOLOGY_UPDATED",
                "timestamp", Instant.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/topology/changes", (Object) event);
    }
}
