/**
 * Runtime type validation utilities for WebSocket messages.
 * Provides type guards to validate data received from the backend.
 */

import type {
  Agent,
  AgentState,
  Call,
  CallState,
  QueuedCall,
  QueueStats,
  AgentSummary,
  SystemStatus,
  InfraServerType,
  ServerHealthStatus,
  InfrastructureNode,
  InfrastructureEdge,
  InfrastructureTopology,
  InfrastructureSummary,
} from '../types';

const VALID_AGENT_STATES: AgentState[] = ['ONLINE', 'ON_CALL', 'AWAY', 'UNAVAILABLE'];
const VALID_CALL_STATES: CallState[] = ['TALKING', 'ON_HOLD'];
const VALID_INFRA_SERVER_TYPES: InfraServerType[] = ['SIP', 'MEDIA'];
const VALID_HEALTH_STATUSES: ServerHealthStatus[] = ['HEALTHY', 'DEGRADED', 'UNHEALTHY', 'UNKNOWN'];

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isString(value: unknown): value is string {
  return typeof value === 'string';
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && !isNaN(value);
}

function isBoolean(value: unknown): value is boolean {
  return typeof value === 'boolean';
}

function isStringOrNull(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isNumberOrNull(value: unknown): value is number | null {
  return value === null || (typeof value === 'number' && !isNaN(value));
}

export function isValidAgentState(value: unknown): value is AgentState {
  return isString(value) && VALID_AGENT_STATES.includes(value as AgentState);
}

export function isValidCallState(value: unknown): value is CallState {
  return isString(value) && VALID_CALL_STATES.includes(value as CallState);
}

export function isValidAgent(value: unknown): value is Agent {
  if (!isObject(value)) return false;
  return (
    isString(value.id) &&
    isString(value.name) &&
    isValidAgentState(value.state) &&
    isString(value.stateChangedAt) &&
    isStringOrNull(value.currentCallId) &&
    isStringOrNull(value.lastCallOriginator) &&
    isStringOrNull(value.lastCallStartTime) &&
    isStringOrNull(value.lastCallEndTime) &&
    isNumberOrNull(value.lastCallDurationSeconds)
  );
}

export function isValidAgentArray(value: unknown): value is Agent[] {
  return Array.isArray(value) && value.every(isValidAgent);
}

export function isValidCall(value: unknown): value is Call {
  if (!isObject(value)) return false;
  return (
    isString(value.id) &&
    isString(value.originator) &&
    isString(value.agentId) &&
    isString(value.agentName) &&
    isString(value.startTime) &&
    isValidCallState(value.state)
  );
}

export function isValidCallArray(value: unknown): value is Call[] {
  return Array.isArray(value) && value.every(isValidCall);
}

export function isValidQueuedCall(value: unknown): value is QueuedCall {
  if (!isObject(value)) return false;
  return (
    isString(value.id) &&
    isString(value.originator) &&
    isString(value.queuedAt) &&
    isNumber(value.priority) &&
    isString(value.skill)
  );
}

export function isValidQueuedCallArray(value: unknown): value is QueuedCall[] {
  return Array.isArray(value) && value.every(isValidQueuedCall);
}

export function isValidQueueStats(value: unknown): value is QueueStats {
  if (!isObject(value)) return false;
  return (
    isNumber(value.queuedCount) &&
    isNumber(value.avgWaitSeconds) &&
    isNumber(value.longestWaitSeconds)
  );
}

export function isValidAgentSummary(value: unknown): value is AgentSummary {
  if (!isObject(value)) return false;
  return (
    isNumber(value.online) &&
    isNumber(value.onCall) &&
    isNumber(value.away) &&
    isNumber(value.unavailable) &&
    isNumber(value.total)
  );
}

export function isValidSystemStatus(value: unknown): value is SystemStatus {
  if (!isObject(value)) return false;
  return (
    isBoolean(value.redisConnected) &&
    isString(value.lastUpdated) &&
    isStringOrNull(value.errorMessage)
  );
}

export function isValidQueueMessage(value: unknown): value is { calls: QueuedCall[]; stats: QueueStats } {
  if (!isObject(value)) return false;
  const calls = value.calls;
  const stats = value.stats;
  return (
    (Array.isArray(calls) && calls.every(isValidQueuedCall)) &&
    isValidQueueStats(stats)
  );
}

export function isValidInfraServerType(value: unknown): value is InfraServerType {
  return isString(value) && VALID_INFRA_SERVER_TYPES.includes(value as InfraServerType);
}

export function isValidServerHealthStatus(value: unknown): value is ServerHealthStatus {
  return isString(value) && VALID_HEALTH_STATUSES.includes(value as ServerHealthStatus);
}

export function isValidInfrastructureNode(value: unknown): value is InfrastructureNode {
  if (!isObject(value)) return false;
  const position = value.position;
  const hasValidPosition = position === undefined || (
    isObject(position) &&
    isNumber(position.x) &&
    isNumber(position.y)
  );
  return (
    isString(value.id) &&
    isValidInfraServerType(value.type) &&
    isString(value.hostname) &&
    isString(value.ipAddress) &&
    isString(value.startTime) &&
    isNumber(value.activeSessions) &&
    isNumber(value.maxSessions) &&
    isValidServerHealthStatus(value.healthStatus) &&
    hasValidPosition
  );
}

export function isValidInfrastructureEdge(value: unknown): value is InfrastructureEdge {
  if (!isObject(value)) return false;
  return (
    isString(value.id) &&
    isString(value.sourceId) &&
    isString(value.targetId)
  );
}

export function isValidInfrastructureTopology(value: unknown): value is InfrastructureTopology {
  if (!isObject(value)) return false;
  return (
    Array.isArray(value.nodes) &&
    value.nodes.every(isValidInfrastructureNode) &&
    Array.isArray(value.edges) &&
    value.edges.every(isValidInfrastructureEdge) &&
    isString(value.lastUpdated)
  );
}

export function isValidInfrastructureSummary(value: unknown): value is InfrastructureSummary {
  if (!isObject(value)) return false;
  return (
    isNumber(value.sipServerCount) &&
    isNumber(value.mediaServerCount) &&
    isNumber(value.totalActiveSessions) &&
    isNumber(value.healthyCount) &&
    isNumber(value.degradedCount) &&
    isNumber(value.unhealthyCount)
  );
}

/**
 * Safely parse JSON with type validation.
 * Returns null if parsing or validation fails.
 */
export function safeParseJson<T>(
  json: string,
  validator: (value: unknown) => value is T
): T | null {
  try {
    const parsed = JSON.parse(json);
    if (validator(parsed)) {
      return parsed;
    }
    console.warn('JSON validation failed for:', json.substring(0, 100));
    return null;
  } catch (e) {
    console.error('JSON parse error:', e);
    return null;
  }
}
