/**
 * Runtime type validation utilities for WebSocket messages.
 * Provides type guards to validate data received from the backend.
 */

import type {
  AgentSummary,
  SystemStatus,
  InfraServerType,
  ServerHealthStatus,
  InfrastructureNode,
  InfrastructureEdge,
  InfrastructureTopology,
  InfrastructureSummary,
} from '../types';

const VALID_INFRA_SERVER_TYPES: InfraServerType[] = ['TRUNK', 'SBC', 'SIP', 'MEDIA'];
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
  // Check core required fields - new fields like datacenter, metrics, etc. are optional in validation
  // to allow backward compatibility, but they will be present in the actual data
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
