package com.example.agentmonitor.service;

import com.example.agentmonitor.model.InfrastructureTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for infrastructure topology data.
 *
 * This service manages the infrastructure topology state and handles:
 * - Initial topology generation via the data provider
 * - Periodic updates via the data provider
 * - Broadcasting updates via WebSocket
 *
 * The actual data generation is delegated to an InfrastructureDataProvider
 * implementation, which can be swapped based on the active Spring profile.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InfrastructureService {

    private final SimpMessagingTemplate messagingTemplate;
    private final InfrastructureDataProvider dataProvider;

    private final AtomicReference<InfrastructureTopology> currentTopology = new AtomicReference<>();

    @PostConstruct
    public void init() {
        InfrastructureTopology topology = dataProvider.generateInitialTopology();
        currentTopology.set(topology);
        log.info("Infrastructure topology initialized with {} nodes using {}",
                topology.getNodes().size(),
                dataProvider.getClass().getSimpleName());
    }

    /**
     * Get current infrastructure topology.
     */
    public InfrastructureTopology getTopology() {
        return currentTopology.get();
    }

    /**
     * Broadcast infrastructure updates via WebSocket.
     */
    public void broadcastTopology() {
        InfrastructureTopology topology = currentTopology.get();
        messagingTemplate.convertAndSend("/topic/infrastructure", topology);
    }

    /**
     * Update topology with changes from the data provider every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void updateTopology() {
        InfrastructureTopology current = currentTopology.get();
        if (current == null) return;

        InfrastructureTopology updated = dataProvider.updateTopology(current);
        currentTopology.set(updated);
        broadcastTopology();
    }
}
