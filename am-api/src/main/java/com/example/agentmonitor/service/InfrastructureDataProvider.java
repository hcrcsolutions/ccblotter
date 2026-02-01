package com.example.agentmonitor.service;

import com.example.agentmonitor.model.InfrastructureTopology;

/**
 * Interface for providing infrastructure topology data.
 *
 * Implementations can provide mock data for development/testing
 * or real data from actual infrastructure monitoring systems.
 */
public interface InfrastructureDataProvider {

    /**
     * Generate the initial topology.
     * Called once during service initialization.
     *
     * @return the initial infrastructure topology
     */
    InfrastructureTopology generateInitialTopology();

    /**
     * Update the topology with simulated or real changes.
     * Called periodically to refresh the topology state.
     *
     * @param currentTopology the current topology state
     * @return the updated topology
     */
    InfrastructureTopology updateTopology(InfrastructureTopology currentTopology);
}
