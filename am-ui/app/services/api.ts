import type { Agent, Call, AgentSummary, SystemStatus } from '../types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/**
 * API client for REST endpoints.
 * Used as fallback when WebSocket is unavailable.
 */
export const api = {
  /**
   * Get system health status.
   */
  async getHealth(): Promise<SystemStatus> {
    const response = await fetch(`${API_BASE_URL}/health`);
    if (!response.ok) {
      throw new Error('Failed to fetch health status');
    }
    return response.json();
  },

  /**
   * Get all agents.
   */
  async getAgents(): Promise<Agent[]> {
    const response = await fetch(`${API_BASE_URL}/agents`);
    if (response.status === 503) {
      throw new Error('Redis unavailable');
    }
    if (!response.ok) {
      throw new Error('Failed to fetch agents');
    }
    return response.json();
  },

  /**
   * Get agent summary.
   */
  async getAgentSummary(): Promise<AgentSummary> {
    const response = await fetch(`${API_BASE_URL}/agents/summary`);
    if (response.status === 503) {
      throw new Error('Redis unavailable');
    }
    if (!response.ok) {
      throw new Error('Failed to fetch agent summary');
    }
    return response.json();
  },

  /**
   * Get active calls.
   */
  async getCalls(): Promise<Call[]> {
    const response = await fetch(`${API_BASE_URL}/calls`);
    if (response.status === 503) {
      throw new Error('Redis unavailable');
    }
    if (!response.ok) {
      throw new Error('Failed to fetch calls');
    }
    return response.json();
  },

  // ============= Test endpoints =============

  /**
   * Create a test agent.
   */
  async createTestAgent(id: string, name: string, state: string = 'ONLINE'): Promise<Agent> {
    const response = await fetch(`${API_BASE_URL}/test/agents`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, name, state }),
    });
    if (!response.ok) {
      throw new Error('Failed to create test agent');
    }
    return response.json();
  },

  /**
   * Start a test call.
   */
  async startTestCall(originator: string, agentId: string): Promise<Call> {
    const response = await fetch(`${API_BASE_URL}/test/calls`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ originator, agentId }),
    });
    if (!response.ok) {
      throw new Error('Failed to start test call');
    }
    return response.json();
  },

  /**
   * End a test call.
   */
  async endTestCall(callId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/test/calls/${callId}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      throw new Error('Failed to end test call');
    }
  },

  /**
   * Update agent state.
   */
  async updateAgentState(agentId: string, state: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/test/agents/${agentId}/state`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ state }),
    });
    if (!response.ok) {
      throw new Error('Failed to update agent state');
    }
  },

  /**
   * Delete a test agent.
   */
  async deleteTestAgent(agentId: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/test/agents/${agentId}`, {
      method: 'DELETE',
    });
    if (!response.ok) {
      throw new Error('Failed to delete test agent');
    }
  },
};
