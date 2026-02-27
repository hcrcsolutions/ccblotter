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
 * Call state for active calls.
 */
export type CallState = 'TALKING' | 'ON_HOLD';

/**
 * Represents an active call.
 */
export interface Call {
  id: string;
  originator: string;
  agentId: string;
  agentName: string;
  startTime: string; // ISO timestamp
  state: CallState;
}

/**
 * Represents a call waiting in queue.
 */
export interface QueuedCall {
  id: string;
  originator: string;
  queuedAt: string; // ISO timestamp
  priority: number; // 1 = highest
  skill: string;
}

/**
 * Queue statistics.
 */
export interface QueueStats {
  queuedCount: number;
  avgWaitSeconds: number;
  longestWaitSeconds: number;
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
 * Infrastructure server types.
 */
export type InfraServerType = 'TRUNK' | 'SBC' | 'SIP' | 'MEDIA';

/**
 * Server health status.
 */
export type ServerHealthStatus = 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' | 'UNKNOWN';

/**
 * Quality metrics for a node.
 */
export interface NodeMetrics {
  cpuPercent: number;
  memoryPercent: number;
  latencyMs: number;
  jitterMs: number;
  packetLossPercent: number;
  errorRate: number;
  mosScore: number; // 10-50 (1.0-5.0 scaled)
}

/**
 * Session breakdown by direction and state.
 */
export interface SessionBreakdown {
  inboundSessions: number;
  outboundSessions: number;
  ivrSessions: number;
  queueSessions: number;
  agentSessions: number;
  onHoldSessions: number;
}

/**
 * Trend data point for history.
 */
export interface TrendDataPoint {
  timestamp: string; // ISO timestamp
  activeSessions: number;
  cpuPercent: number;
}

/**
 * Datacenter summary statistics.
 */
export interface DatacenterSummary {
  id: string;
  region: string;
  totalNodes: number;
  healthyNodes: number;
  degradedNodes: number;
  unhealthyNodes: number;
  totalSessions: number;
  totalCapacity: number;
  utilizationPercent: number;
}

/**
 * Represents an infrastructure server node.
 */
export interface InfrastructureNode {
  id: string;
  type: InfraServerType;
  hostname: string;
  ipAddress: string;
  startTime: string; // ISO timestamp
  activeSessions: number;
  maxSessions: number;
  healthStatus: ServerHealthStatus;
  position?: { x: number; y: number }; // Optional saved position

  // Datacenter grouping
  datacenter: string; // e.g., "dc1", "dc2"
  region: string; // e.g., "us-east", "us-west"

  // Quality metrics
  metrics: NodeMetrics | null;

  // Session breakdown (null for TRUNK/SBC)
  sessionBreakdown: SessionBreakdown | null;

  // Trend data (last 5 minutes, ~60 points at 5-sec intervals)
  trendHistory: TrendDataPoint[];

  // Trunk-specific fields
  carrierName: string | null; // Only for TRUNK type
  trunkGroup: string | null; // e.g., "primary", "backup"

  // Maintenance mode
  maintenanceMode: boolean;
}

/**
 * Represents a connection between infrastructure nodes.
 */
export interface InfrastructureEdge {
  id: string;
  sourceId: string;
  targetId: string;
  bandwidthMbps?: number;
  latencyMs?: number;
  activeFlows?: number;
}

/**
 * Complete infrastructure topology.
 */
export interface InfrastructureTopology {
  nodes: InfrastructureNode[];
  edges: InfrastructureEdge[];
  lastUpdated: string; // ISO timestamp
}

/**
 * Summary of infrastructure status.
 */
export interface InfrastructureSummary {
  sipServerCount: number;
  mediaServerCount: number;
  trunkCount: number;
  sbcCount: number;
  totalActiveSessions: number;
  healthyCount: number;
  degradedCount: number;
  unhealthyCount: number;
  nodeHealthyCount: number;    // SIP + Media only
  nodeDegradedCount: number;   // SIP + Media only
  nodeUnhealthyCount: number;  // SIP + Media only
  totalCapacity: number;
  utilizationPercent: number;
  headroomSessions: number;
  sessionBreakdown: SessionBreakdown;
  avgLatencyMs: number;
  avgJitterMs: number;
  avgErrorRate: number;
  datacenterSummaries: DatacenterSummary[];
}

// ============================================================================
// IVR Types
// ============================================================================

/**
 * IVR session states matching backend enum.
 */
export type IvrState = 'ACTIVE' | 'AUTHENTICATING' | 'TRANSFERRING' | 'COMPLETED' | 'ABANDONED' | 'FAILED';

/**
 * IVR step types matching backend enum.
 */
export type IvrStepType = 'PLAY' | 'SAY' | 'CAPTURE' | 'AUTHENTICATE' | 'TRANSFER' | 'HANGUP' | 'BRANCH';

/**
 * IVR session row from grid query.
 */
export interface IvrSessionRow {
  id: string;
  callId: string;
  originator: string;
  flowId: string;
  state: IvrState;
  currentStep: string;
  stepCount: number;
  startTime: string;
  endTime: string | null;
  authenticated: boolean;
}

/**
 * Single step in an IVR session (from detail endpoint).
 */
export interface IvrStep {
  id: string;
  sessionId: string;
  stepType: IvrStepType;
  stepName: string;
  prompt: string | null;
  input: string | null;
  outcome: string;
  startTime: string;
  endTime: string;
  latencyMs: number;
  retryAttempt: number;
  error: string | null;
  audioUrl: string | null;
}

/**
 * Summary of IVR session states for dashboard cards.
 */
export interface IvrSessionSummary {
  active: number;
  authenticating: number;
  completed: number;
  abandoned: number;
  failed: number;
  total: number;
}

// ============================================================================
// Grid Types - For AG Grid infinite row model (server-side pagination)
// ============================================================================

export interface GridSortItem {
  colId: string;
  sort: 'asc' | 'desc';
}

export interface GridFilterItem {
  filterType: 'text' | 'number';
  type: string;
  filter: string;
  filterTo?: string;
}

export interface GridRequest {
  startRow: number;
  endRow: number;
  sortModel: GridSortItem[];
  filterModel: Record<string, GridFilterItem>;
}

export interface GridResponse<T> {
  rows: T[];
  lastRow: number;
  metadata?: Record<string, unknown>;
}

/**
 * Agent enriched with current call data (from POST /api/agents/query).
 */
export interface AgentRow {
  id: string;
  name: string;
  state: AgentState;
  stateChangedAt: string;
  currentCallId: string | null;
  currentCaller: string | null;
  callStartTime: string | null;
  callState: CallState | null;
  lastCallOriginator: string | null;
  lastCallStartTime: string | null;
  lastCallEndTime: string | null;
  lastCallDurationSeconds: number | null;
}

/**
 * Complete dashboard state from WebSocket.
 * Note: agents, calls, queuedCalls, queueStats are now fetched via REST grid endpoints.
 */
export interface DashboardState {
  systemStatus: SystemStatus;
  connectionState: ConnectionState;
  infrastructure: InfrastructureTopology;
  infrastructureSummary: InfrastructureSummary;
  reconnect: () => void;
}

// ============================================================================
// Editor Types - For infrastructure configuration
// ============================================================================

/**
 * Datacenter configuration for editor.
 */
export interface Datacenter {
  id: string;
  region: string;
  displayName: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to create/update a datacenter.
 */
export interface DatacenterRequest {
  id: string;
  region: string;
  displayName?: string;
}

/**
 * Node registration request for creating Trunks/SBCs.
 */
export interface NodeRegistrationRequest {
  id: string;
  type: InfraServerType;
  hostname: string;
  ipAddress: string;
  datacenter: string;
  region?: string;
  maxSessions: number;
  carrierName?: string;
  trunkGroup?: string;
  metadata?: Record<string, unknown>;
}

/**
 * Node registration response.
 */
export interface NodeRegistrationResponse {
  id: string;
  status: ServerHealthStatus;
  registeredAt: string;
  reregisteredAt?: string;
  reregistered: boolean;
  heartbeatIntervalSeconds: number;
  heartbeatTimeoutSeconds: number;
  message?: string;
}

/**
 * Node query response from API.
 */
export interface NodeQueryResponse {
  nodes: NodeSummary[];
  total: number;
  filter: {
    type?: InfraServerType;
    datacenter?: string;
    status?: ServerHealthStatus;
  };
}

/**
 * Node summary for query results.
 */
export interface NodeSummary {
  id: string;
  type: InfraServerType;
  hostname: string;
  ipAddress: string;
  datacenter: string;
  region: string;
  status: ServerHealthStatus;
  maxSessions: number;
  activeSessions: number;
  availableCapacity: number;
  lastHeartbeat: string | null;
}
