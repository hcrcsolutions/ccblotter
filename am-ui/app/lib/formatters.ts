/**
 * Shared formatting utilities for consistent display across the application.
 */

export type DurationFormat = 'long' | 'short' | 'compact';

/**
 * Format duration from seconds to human readable string.
 *
 * @param seconds - Duration in seconds (null returns '-')
 * @param format - Output format:
 *   - 'long': "2h 30m" or "5m 30s" or "30s"
 *   - 'short': "2:30:00" or "5:30"
 *   - 'compact': "2h" or "30m" or "30s" (largest unit only)
 */
export function formatDuration(
  seconds: number | null,
  format: DurationFormat = 'long'
): string {
  if (seconds == null || seconds < 0) return '-';

  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  switch (format) {
    case 'long':
      if (hours > 0) {
        return `${hours}h ${minutes}m`;
      }
      if (minutes > 0) {
        return `${minutes}m ${secs}s`;
      }
      return `${secs}s`;

    case 'short':
      if (hours > 0) {
        return `${hours}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
      }
      return `${minutes}:${secs.toString().padStart(2, '0')}`;

    case 'compact':
      if (hours > 0) {
        return `${hours}h`;
      }
      if (minutes > 0) {
        return `${minutes}m`;
      }
      return `${secs}s`;

    default:
      return formatDuration(seconds, 'long');
  }
}

/**
 * Format a timestamp as localized time string.
 *
 * @param timestamp - ISO timestamp string or null
 * @returns Formatted time like "2:30:45 PM" or "-" if null
 */
export function formatTime(timestamp: string | null): string {
  if (!timestamp) return '-';

  const date = new Date(timestamp);
  if (isNaN(date.getTime())) return '-';

  return date.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/**
 * Calculate duration in seconds from a timestamp to now.
 * Returns null for invalid inputs, 0 for future timestamps.
 *
 * @param timestamp - ISO timestamp string or null
 * @returns Duration in seconds, null if invalid, 0 if future
 */
export function calculateDuration(timestamp: string | null): number | null {
  if (!timestamp) return null;

  const start = new Date(timestamp).getTime();

  // Check for invalid date
  if (isNaN(start)) {
    return null;
  }

  const now = Date.now();
  const duration = Math.floor((now - start) / 1000);

  // Return 0 for future timestamps (clock skew protection)
  return duration < 0 ? 0 : duration;
}
