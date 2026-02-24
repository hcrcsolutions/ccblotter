/**
 * SSR (Server-Side Rendering) utilities for consistent client/server detection.
 */

/**
 * Check if code is running in browser environment.
 * Use this for one-time checks (not reactive).
 */
function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

/**
 * Check if code is running in server environment.
 * Use this for one-time checks (not reactive).
 */
export function isServer(): boolean {
  return typeof window === 'undefined';
}

/**
 * Safely access localStorage with fallback.
 * Returns null if not in browser or if localStorage is not available.
 */
function safeLocalStorage(): Storage | null {
  if (!isBrowser()) return null;
  try {
    return window.localStorage;
  } catch {
    // localStorage may be disabled or throw in some contexts
    return null;
  }
}

/**
 * Get a value from localStorage safely.
 * Returns null if not available or if key doesn't exist.
 */
export function getLocalStorageItem(key: string): string | null {
  const storage = safeLocalStorage();
  if (!storage) return null;
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

/**
 * Set a value in localStorage safely.
 * Returns true if successful, false otherwise.
 */
export function setLocalStorageItem(key: string, value: string): boolean {
  const storage = safeLocalStorage();
  if (!storage) return false;
  try {
    storage.setItem(key, value);
    return true;
  } catch {
    return false;
  }
}

/**
 * Remove a value from localStorage safely.
 * Returns true if successful, false otherwise.
 */
export function removeLocalStorageItem(key: string): boolean {
  const storage = safeLocalStorage();
  if (!storage) return false;
  try {
    storage.removeItem(key);
    return true;
  } catch {
    return false;
  }
}
