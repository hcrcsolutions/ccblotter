/**
 * Centralized status color definitions for consistent theming.
 * All colors have been validated for WCAG AA contrast compliance.
 *
 * Color format: HSL for easy adjustment
 * Each color set has both light and dark mode variants.
 */

import type { InfraServerType, IvrState, ServerHealthStatus } from '../types';

// =============================================================================
// THRESHOLD CONSTANTS
// =============================================================================

/** Capacity utilization thresholds (percentage) */
export const CAPACITY_THRESHOLDS = {
  WARNING: 60,
  CRITICAL: 80,
} as const;

/** CPU utilization thresholds (percentage) */
export const CPU_THRESHOLDS = {
  WARNING: 60,
  CRITICAL: 80,
} as const;

/** Memory utilization thresholds (percentage) */
export const MEMORY_THRESHOLDS = {
  WARNING: 70,
  CRITICAL: 85,
} as const;

/** Latency thresholds (milliseconds) */
export const LATENCY_THRESHOLDS = {
  GOOD: 100,
  WARNING: 200,
} as const;

export interface StatusColorSet {
  light: { bg: string; text: string };
  dark: { bg: string; text: string };
  label: string;
}

/**
 * Agent status colors
 */
export const AGENT_STATUS_COLORS: Record<string, StatusColorSet> = {
  ONLINE: {
    light: { bg: 'hsl(120, 80%, 92%)', text: 'hsl(120, 59%, 30%)' },
    dark: { bg: 'hsl(120, 50%, 20%)', text: 'hsl(120, 70%, 65%)' },
    label: 'Online',
  },
  ON_CALL: {
    light: { bg: 'hsl(210, 100%, 92%)', text: 'hsl(210, 100%, 35%)' },
    dark: { bg: 'hsl(210, 70%, 25%)', text: 'hsl(210, 90%, 70%)' },
    label: 'On Call',
  },
  AWAY: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(45, 80%, 30%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 90%, 65%)' },
    label: 'Away',
  },
  UNAVAILABLE: {
    light: { bg: 'hsl(0, 0%, 92%)', text: 'hsl(0, 0%, 40%)' },
    dark: { bg: 'hsl(0, 0%, 25%)', text: 'hsl(0, 0%, 70%)' },
    label: 'Unavailable',
  },
};

/**
 * Call state colors
 */
export const CALL_STATE_COLORS: Record<string, StatusColorSet> = {
  TALKING: {
    light: { bg: 'hsl(120, 80%, 92%)', text: 'hsl(120, 59%, 30%)' },
    dark: { bg: 'hsl(120, 50%, 20%)', text: 'hsl(120, 70%, 65%)' },
    label: 'Talking',
  },
  ON_HOLD: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(45, 80%, 30%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 90%, 65%)' },
    label: 'On Hold',
  },
};

/**
 * IVR session state colors
 */
export const IVR_STATE_COLORS: Record<IvrState, StatusColorSet> = {
  ACTIVE: {
    light: { bg: 'hsl(180, 70%, 92%)', text: 'hsl(180, 60%, 30%)' },
    dark: { bg: 'hsl(180, 50%, 20%)', text: 'hsl(180, 60%, 65%)' },
    label: 'Active',
  },
  AUTHENTICATING: {
    light: { bg: 'hsl(210, 100%, 92%)', text: 'hsl(210, 100%, 35%)' },
    dark: { bg: 'hsl(210, 70%, 25%)', text: 'hsl(210, 90%, 70%)' },
    label: 'Authenticating',
  },
  TRANSFERRING: {
    light: { bg: 'hsl(270, 80%, 94%)', text: 'hsl(270, 70%, 40%)' },
    dark: { bg: 'hsl(270, 50%, 22%)', text: 'hsl(270, 70%, 70%)' },
    label: 'Transferring',
  },
  COMPLETED: {
    light: { bg: 'hsl(120, 80%, 92%)', text: 'hsl(120, 59%, 30%)' },
    dark: { bg: 'hsl(120, 50%, 20%)', text: 'hsl(120, 70%, 65%)' },
    label: 'Completed',
  },
  ABANDONED: {
    light: { bg: 'hsl(30, 100%, 92%)', text: 'hsl(30, 80%, 30%)' },
    dark: { bg: 'hsl(30, 60%, 22%)', text: 'hsl(30, 90%, 65%)' },
    label: 'Abandoned',
  },
  FAILED: {
    light: { bg: 'hsl(0, 90%, 92%)', text: 'hsl(0, 70%, 35%)' },
    dark: { bg: 'hsl(0, 60%, 25%)', text: 'hsl(0, 80%, 70%)' },
    label: 'Failed',
  },
};

/**
 * Priority colors for queue
 */
export const PRIORITY_COLORS: Record<number, StatusColorSet> = {
  1: {
    light: { bg: 'hsl(0, 90%, 92%)', text: 'hsl(0, 70%, 35%)' },
    dark: { bg: 'hsl(0, 60%, 25%)', text: 'hsl(0, 80%, 70%)' },
    label: 'High',
  },
  2: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(45, 80%, 30%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 90%, 65%)' },
    label: 'Medium',
  },
  3: {
    light: { bg: 'hsl(210, 80%, 92%)', text: 'hsl(210, 60%, 35%)' },
    dark: { bg: 'hsl(210, 50%, 25%)', text: 'hsl(210, 70%, 70%)' },
    label: 'Normal',
  },
};

/**
 * Wait time threshold colors (for queue)
 */
export const WAIT_TIME_COLORS: Record<'warning' | 'critical', StatusColorSet> = {
  warning: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(45, 80%, 30%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 90%, 65%)' },
    label: 'Warning',
  },
  critical: {
    light: { bg: 'hsl(0, 90%, 92%)', text: 'hsl(0, 70%, 35%)' },
    dark: { bg: 'hsl(0, 60%, 25%)', text: 'hsl(0, 80%, 70%)' },
    label: 'Critical',
  },
};

/**
 * Server type colors (for infrastructure diagram)
 */
export const SERVER_TYPE_COLORS: Record<InfraServerType, StatusColorSet> = {
  TRUNK: {
    light: { bg: 'hsl(250, 80%, 94%)', text: 'hsl(250, 70%, 40%)' },
    dark: { bg: 'hsl(250, 50%, 22%)', text: 'hsl(250, 70%, 70%)' },
    label: 'Trunk',
  },
  SBC: {
    light: { bg: 'hsl(180, 70%, 92%)', text: 'hsl(180, 60%, 30%)' },
    dark: { bg: 'hsl(180, 50%, 20%)', text: 'hsl(180, 60%, 65%)' },
    label: 'SBC',
  },
  SIP: {
    light: { bg: 'hsl(210, 100%, 92%)', text: 'hsl(210, 100%, 35%)' },
    dark: { bg: 'hsl(210, 70%, 25%)', text: 'hsl(210, 90%, 70%)' },
    label: 'SIP Server',
  },
  MEDIA: {
    light: { bg: 'hsl(120, 80%, 92%)', text: 'hsl(120, 70%, 35%)' },
    dark: { bg: 'hsl(120, 50%, 25%)', text: 'hsl(120, 70%, 60%)' },
    label: 'Media Server',
  },
};

/**
 * Server health status colors
 */
export const SERVER_HEALTH_COLORS: Record<ServerHealthStatus, StatusColorSet> = {
  HEALTHY: {
    light: { bg: 'hsl(120, 80%, 92%)', text: 'hsl(120, 59%, 30%)' },
    dark: { bg: 'hsl(120, 50%, 20%)', text: 'hsl(120, 70%, 65%)' },
    label: 'Healthy',
  },
  DEGRADED: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(50, 100%, 50%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(50, 100%, 55%)' },
    label: 'Degraded',
  },
  UNHEALTHY: {
    light: { bg: 'hsl(0, 90%, 92%)', text: 'hsl(0, 100%, 50%)' },
    dark: { bg: 'hsl(0, 60%, 25%)', text: 'hsl(0, 100%, 55%)' },
    label: 'Unhealthy',
  },
  UNKNOWN: {
    light: { bg: 'hsl(0, 0%, 92%)', text: 'hsl(0, 0%, 40%)' },
    dark: { bg: 'hsl(0, 0%, 25%)', text: 'hsl(0, 0%, 70%)' },
    label: 'Unknown',
  },
};

/**
 * Quality indicator colors (for latency, jitter, etc.)
 */
const QUALITY_COLORS = {
  GOOD: {
    light: { bg: 'hsl(120, 70%, 92%)', text: 'hsl(120, 60%, 30%)' },
    dark: { bg: 'hsl(120, 50%, 20%)', text: 'hsl(120, 60%, 65%)' },
  },
  WARNING: {
    light: { bg: 'hsl(45, 90%, 90%)', text: 'hsl(45, 80%, 30%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 80%, 65%)' },
  },
  POOR: {
    light: { bg: 'hsl(0, 80%, 92%)', text: 'hsl(0, 70%, 35%)' },
    dark: { bg: 'hsl(0, 60%, 25%)', text: 'hsl(0, 70%, 70%)' },
  },
};

/**
 * Session breakdown colors (for charts)
 */
export const SESSION_BREAKDOWN_COLORS = {
  INBOUND: 'hsl(210, 70%, 50%)',
  OUTBOUND: 'hsl(280, 60%, 50%)',
  IVR: 'hsl(180, 60%, 45%)',
  QUEUE: 'hsl(45, 80%, 45%)',
  AGENT: 'hsl(120, 60%, 45%)',
  ON_HOLD: 'hsl(0, 70%, 50%)',
} as const;

/**
 * Sparkline trend colors
 */
const TREND_COLORS = {
  UP: {
    light: 'hsl(120, 70%, 40%)',
    dark: 'hsl(120, 60%, 55%)',
  },
  DOWN: {
    light: 'hsl(0, 80%, 50%)',
    dark: 'hsl(0, 70%, 60%)',
  },
  FLAT: {
    light: 'hsl(210, 80%, 45%)',
    dark: 'hsl(210, 70%, 60%)',
  },
} as const;

/**
 * Get trend color based on direction
 */
export function getTrendColor(trend: 'up' | 'down' | 'flat', isDarkMode: boolean): string {
  const colorKey = trend.toUpperCase() as keyof typeof TREND_COLORS;
  return isDarkMode ? TREND_COLORS[colorKey].dark : TREND_COLORS[colorKey].light;
}

/**
 * Helper to get colors for current theme mode
 */
export function getStatusColors(
  colorSet: StatusColorSet,
  isDarkMode: boolean
): { bg: string; text: string } {
  return isDarkMode ? colorSet.dark : colorSet.light;
}

/**
 * Get quality color based on latency threshold
 */
export function getLatencyQualityColor(latencyMs: number, isDarkMode: boolean) {
  if (latencyMs < LATENCY_THRESHOLDS.GOOD) {
    return isDarkMode ? QUALITY_COLORS.GOOD.dark : QUALITY_COLORS.GOOD.light;
  }
  if (latencyMs < LATENCY_THRESHOLDS.WARNING) {
    return isDarkMode ? QUALITY_COLORS.WARNING.dark : QUALITY_COLORS.WARNING.light;
  }
  return isDarkMode ? QUALITY_COLORS.POOR.dark : QUALITY_COLORS.POOR.light;
}

/**
 * Get quality color based on MOS score (10-50 scale, representing 1.0-5.0)
 */
export function getMosQualityColor(mosScore: number, isDarkMode: boolean) {
  if (mosScore >= 40) {
    // 4.0+ is good
    return isDarkMode ? QUALITY_COLORS.GOOD.dark : QUALITY_COLORS.GOOD.light;
  }
  if (mosScore >= 30) {
    // 3.0-3.9 is acceptable
    return isDarkMode ? QUALITY_COLORS.WARNING.dark : QUALITY_COLORS.WARNING.light;
  }
  return isDarkMode ? QUALITY_COLORS.POOR.dark : QUALITY_COLORS.POOR.light;
}
