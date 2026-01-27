/**
 * Agent states matching backend enum.
 */
export type AgentState = 'ONLINE' | 'ON_CALL' | 'AWAY' | 'UNAVAILABLE';

/**
 * Represents a call center agent.
 */
export interface Agent {
  id: string;
  name: string;
  state: AgentState;
  stateChangedAt: string; // ISO timestamp
  currentCallId: string | null;

  // Last call information (for agents not currently on a call)
  lastCallOriginator: string | null;
  lastCallStartTime: string | null; // ISO timestamp
  lastCallEndTime: string | null; // ISO timestamp
  lastCallDurationSeconds: number | null;
}

/**
 * Represents an active call.
 */
export interface Call {
  id: string;
  originator: string;
  agentId: string;
  agentName: string;
  startTime: string; // ISO timestamp
}

/**
 * Summary of agent states for dashboard.
 */
export interface AgentSummary {
  online: number;
  onCall: number;
  away: number;
  unavailable: number;
  total: number;
}

/**
 * System health status.
 */
export interface SystemStatus {
  redisConnected: boolean;
  lastUpdated: string; // ISO timestamp
  errorMessage: string | null;
}

/**
 * WebSocket connection state.
 */
export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'error';

/**
 * Complete dashboard state from WebSocket.
 */
export interface DashboardState {
  agents: Agent[];
  calls: Call[];
  summary: AgentSummary;
  systemStatus: SystemStatus;
  connectionState: ConnectionState;
}
