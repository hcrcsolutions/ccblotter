/**
 * Centralized status color definitions for consistent theming.
 * All colors have been validated for WCAG AA contrast compliance.
 *
 * Color format: HSL for easy adjustment
 * Each color set has both light and dark mode variants.
 */

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
export const WAIT_TIME_COLORS = {
  warning: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(45, 80%, 30%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 90%, 65%)' },
  },
  critical: {
    light: { bg: 'hsl(0, 90%, 92%)', text: 'hsl(0, 70%, 35%)' },
    dark: { bg: 'hsl(0, 60%, 25%)', text: 'hsl(0, 80%, 70%)' },
  },
};

/**
 * Helper to get colors for current theme mode
 */
export function getStatusColors(
  colorSet: StatusColorSet,
  isDarkMode: boolean
): { bg: string; text: string } {
  return isDarkMode ? colorSet.dark : colorSet.light;
}
