/**
 * Settings utilities for accessing user configuration.
 */

import { isServer, getLocalStorageItem, removeLocalStorageItem } from './ssr';

const STORAGE_KEY = 'oscc-admin-settings';
const DEFAULT_BACKEND_URL = 'https://localhost:8443';

/**
 * Get the configured backend URL from settings.
 * Falls back to default if not configured or on server.
 */
export function getBackendUrl(): string {
  if (isServer()) {
    return DEFAULT_BACKEND_URL;
  }
  try {
    const stored = getLocalStorageItem(STORAGE_KEY);
    if (stored) {
      const settings = JSON.parse(stored);
      // Validate settings structure
      if (typeof settings !== 'object' || settings === null) {
        throw new Error('Invalid settings format: not an object');
      }
      // Handle new format with multiple configs
      if (Array.isArray(settings.configs) && typeof settings.activeConfigName === 'string') {
        const activeConfig = settings.configs.find(
          (c: unknown): c is { name: string; url: string } =>
            typeof c === 'object' &&
            c !== null &&
            typeof (c as { name?: unknown }).name === 'string' &&
            typeof (c as { url?: unknown }).url === 'string' &&
            (c as { name: string }).name === settings.activeConfigName
        );
        if (activeConfig && activeConfig.url) {
          return activeConfig.url;
        }
      }
      // Handle old format with just backendUrl
      if (typeof settings.backendUrl === 'string' && settings.backendUrl) {
        return settings.backendUrl;
      }
    }
  } catch (e) {
    console.error('Corrupted settings in localStorage, using default:', e);
    // Clear corrupted data to prevent repeated errors
    removeLocalStorageItem(STORAGE_KEY);
  }
  return DEFAULT_BACKEND_URL;
}

/**
 * Get the API base URL (backend URL + /api).
 */
export function getApiBaseUrl(): string {
  return `${getBackendUrl()}/api`;
}
