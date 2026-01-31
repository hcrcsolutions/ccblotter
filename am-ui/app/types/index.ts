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

/**
 * Complete dashboard state from WebSocket.
 */
export interface DashboardState {
  agents: Agent[];
  calls: Call[];
  queuedCalls: QueuedCall[];
  queueStats: QueueStats;
  summary: AgentSummary;
  systemStatus: SystemStatus;
  connectionState: ConnectionState;
  infrastructure: InfrastructureTopology;
  infrastructureSummary: InfrastructureSummary;
  reconnect: () => void;
}
