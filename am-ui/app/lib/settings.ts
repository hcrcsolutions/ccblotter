/**
 * Settings utilities for accessing user configuration.
 */

import { isServer, getLocalStorageItem, removeLocalStorageItem } from './ssr';

const STORAGE_KEY = 'oscc-admin-settings';
const DEFAULT_BACKEND_URL = 'https://localhost:8443';
const DEFAULT_IVR_SERVER_URL = 'http://localhost:8082';

interface StoredConfig {
  name: string;
  url: string;
  ivrServerUrl?: string;
}

function findActiveConfig(settings: Record<string, unknown>): StoredConfig | null {
  if (Array.isArray(settings.configs) && typeof settings.activeConfigName === 'string') {
    return settings.configs.find(
      (c: unknown): c is StoredConfig =>
        typeof c === 'object' &&
        c !== null &&
        typeof (c as { name?: unknown }).name === 'string' &&
        typeof (c as { url?: unknown }).url === 'string' &&
        (c as { name: string }).name === settings.activeConfigName
    ) ?? null;
  }
  return null;
}

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
      const activeConfig = findActiveConfig(settings);
      if (activeConfig && activeConfig.url) {
        return activeConfig.url;
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

/**
 * Get the configured IVR server URL from settings.
 * Reads from the active configuration's ivrServerUrl field.
 * Falls back to default (http://localhost:8082) if not configured or on server.
 */
export function getIvrServerUrl(): string {
  if (isServer()) {
    return DEFAULT_IVR_SERVER_URL;
  }
  try {
    const stored = getLocalStorageItem(STORAGE_KEY);
    if (stored) {
      const settings = JSON.parse(stored);
      if (typeof settings !== 'object' || settings === null) {
        return DEFAULT_IVR_SERVER_URL;
      }
      // Read from active config
      const activeConfig = findActiveConfig(settings);
      if (activeConfig && typeof activeConfig.ivrServerUrl === 'string' && activeConfig.ivrServerUrl) {
        return activeConfig.ivrServerUrl;
      }
      // Legacy: top-level ivrServerUrl
      if (typeof settings.ivrServerUrl === 'string' && settings.ivrServerUrl) {
        return settings.ivrServerUrl;
      }
    }
  } catch {
    // Fall through to default
  }
  return DEFAULT_IVR_SERVER_URL;
}
