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
// Recording Types
// ============================================================================

/**
 * A WAV recording uploaded for IVR prompt playback.
 */
export interface Recording {
  filename: string;
  sizeBytes: number;
  durationSeconds: number;
  sampleRate: number;
  channels: number;
  bitsPerSample: number;
  uploadedAt: string; // ISO timestamp
  url: string;
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

// ============================================================================
// IVR Flow Types
// ============================================================================

/**
 * IVR flow node types matching backend enum.
 */
export type IvrNodeType =
  | 'PLAY_PROMPT'
  | 'COLLECT_DTMF'
  | 'MENU'
  | 'TRANSFER'
  | 'HANGUP'
  | 'SET_VARIABLE'
  | 'CONDITION'
  | 'SUB_FLOW'

  | 'WAIT'
  | 'HTTP_REQUEST'
  | 'ASR_COLLECT'
  | 'NLU_INTENT';

/**
 * IVR flow publication status.
 */
export type IvrFlowStatus = 'DRAFT' | 'PUBLISHED';

/**
 * IVR flow summary from list API.
 */
export interface IvrFlowSummary {
  id: string;
  name: string;
  description: string | null;
  businessUnit: string | null;
  version: number;
  status: IvrFlowStatus;
  createdAt: string;
  updatedAt: string;
}

/**
 * IVR flow node for the visual editor.
 */
export interface IvrFlowNode {
  id: string;
  type: IvrNodeType;
  label: string;
  position: { x: number; y: number };
  config: Record<string, unknown>;
}

/**
 * IVR flow edge for the visual editor.
 */
export interface IvrFlowEdge {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  condition: string | null;
}

/**
 * IVR flow variable declaration.
 */
export interface IvrFlowVariable {
  name: string;
  type: 'STRING' | 'INTEGER' | 'BOOLEAN' | 'DECIMAL';
  defaultValue: string | null;
}

/**
 * IVR flow content (nodes + edges + variables).
 */
export interface IvrFlowContent {
  flowId: string;
  version: number;
  content: string;
  createdAt: string;
  createdBy: string | null;
}

/**
 * IVR flow content version metadata.
 */
export interface IvrFlowVersion {
  version: number;
  createdAt: string;
  createdBy: string | null;
}

/**
 * IVR flow validation issue.
 */
export interface IvrValidationIssue {
  severity: 'ERROR' | 'WARNING';
  nodeId: string | null;
  code: string;
  message: string;
}

/**
 * Result of publishing an IVR flow.
 */
export interface IvrPublishResult {
  flowId: string;
  published: boolean;
  version: number;
  validationIssues: IvrValidationIssue[];
  message: string;
}

// ============================================================================
// Test Scenario Types
// ============================================================================

export type ScenarioStatus = 'DRAFT' | 'READY';
export type ActorRole = 'CALLER' | 'AGENT' | 'SUPERVISOR';
export type CallerProfile = 'PATIENT' | 'IMPATIENT' | 'RANDOM_INPUT';
export type AgentProfile = 'EFFICIENT' | 'NORMAL' | 'SLOW';
export type CallerAction = 'DIAL_IN' | 'IVR_INPUT' | 'WAIT_IN_QUEUE' | 'HANG_UP';
export type AgentAction = 'LOGIN' | 'GO_ONLINE' | 'GO_AWAY' | 'ANSWER_CALL' | 'HOLD_CALL' | 'RESUME_CALL' | 'END_CALL' | 'LOGOUT';
export type SupervisorAction = 'LOGIN' | 'MONITOR';
export type CommonAction = 'WAIT';
export type ScenarioAction = CallerAction | AgentAction | SupervisorAction | CommonAction;
export type EdgeType = 'SEQUENCE' | 'SYNC' | 'TRIGGER' | 'WAIT_FOR';

export interface StepDependency {
  stepId: string;
  type: EdgeType;
}
export type AssertionType = 'AGENT_IN_STATE' | 'CALL_EXISTS' | 'CALL_CONNECTED_WITHIN' | 'QUEUE_LENGTH' | 'IVR_REACHED_NODE';
export type TimingMode = 'REALTIME' | 'COMPRESSED';
export type RunResult = 'PASS' | 'FAIL' | 'ERROR';
export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface ScenarioSummary {
  id: string;
  name: string;
  description: string | null;
  version: number;
  status: ScenarioStatus;
  lastRunResult: RunResult | null;
  lastRunAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ScenarioActor {
  id: string;
  name: string;
  role: ActorRole;
  profile?: CallerProfile | AgentProfile;
  config: Record<string, unknown>;
}

export interface ScenarioStep {
  id: string;
  actorId: string;
  action: ScenarioAction;
  delayMs: number;
  durationMs?: number;
  dependsOn?: StepDependency[];
  config: Record<string, unknown>;
  position?: { x: number; y: number };
}

export interface ScenarioAssertion {
  id: string;
  type: AssertionType;
  afterStepId: string;
  config: Record<string, unknown>;
}

export interface ScenarioSettings {
  defaultTimingMode: TimingMode;
  compressedTimeScale: number;
  cleanupAfterRun: boolean;
}

export interface ScenarioContent {
  actors: ScenarioActor[];
  steps: ScenarioStep[];
  assertions: ScenarioAssertion[];
  settings: ScenarioSettings;
}

export interface ScenarioRun {
  id: string;
  scenarioId: string;
  scenarioVersion: number;
  status: RunStatus;
  timingMode: TimingMode;
  startedAt: string | null;
  finishedAt: string | null;
  result: RunResult | null;
  transcript: TranscriptEntry[] | null;
  assertionResults: AssertionResult[] | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface TranscriptEntry {
  timestamp: string;
  actorId: string;
  actorName: string;
  stepId: string;
  action: string;
  status: string;
  detail: string;
  durationMs?: number;
}

export interface AssertionResult {
  assertionId: string;
  type: AssertionType;
  passed: boolean;
  expected: string;
  actual: string;
  message: string;
}
