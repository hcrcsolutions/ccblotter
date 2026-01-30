'use client';

import * as React from 'react';
import { createContext, useContext, useState, useEffect, useMemo } from 'react';
import { ThemeProvider as MuiThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import getMPTheme from '@/theme/getMPTheme';
import { getLocalStorageItem, setLocalStorageItem } from '../lib/ssr';

type ThemeMode = 'light' | 'dark';

interface ThemeContextType {
  mode: ThemeMode;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType>({
  mode: 'light',
  toggleTheme: () => {},
});

export const THEME_STORAGE_KEY = 'oscc-admin-theme';

/**
 * Get initial theme mode from document attribute (set by inline script)
 * or fall back to light mode during SSR.
 */
function getInitialMode(): ThemeMode {
  if (typeof document !== 'undefined') {
    const attr = document.documentElement.getAttribute('data-theme');
    if (attr === 'dark' || attr === 'light') {
      return attr;
    }
  }
  return 'light';
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  // Initialize from document attribute (already set by inline script)
  // This avoids hydration mismatch since both server and client start with 'light'
  // but client immediately syncs with the DOM attribute
  const [mode, setMode] = useState<ThemeMode>('light');
  const [mounted, setMounted] = useState(false);

  // Sync with actual theme on mount (from inline script's DOM attribute)
  useEffect(() => {
    setMode(getInitialMode());
    setMounted(true);
  }, []);

  // Update localStorage and HTML attribute when mode changes (after mount)
  useEffect(() => {
    if (mounted) {
      setLocalStorageItem(THEME_STORAGE_KEY, mode);
      document.documentElement.setAttribute('data-theme', mode);
    }
  }, [mode, mounted]);

  const toggleTheme = () => {
    setMode((prev) => (prev === 'light' ? 'dark' : 'light'));
  };

  // Use the actual mode after mount, otherwise use initial for SSR
  const effectiveMode = mounted ? mode : 'light';
  const theme = useMemo(() => createTheme(getMPTheme(effectiveMode)), [effectiveMode]);

  const contextValue = useMemo(() => ({ mode: effectiveMode, toggleTheme }), [effectiveMode]);

  // Always render children - the inline script ensures correct initial theme
  // This prevents flash of empty content during hydration
  return (
    <ThemeContext.Provider value={contextValue}>
      <MuiThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider>
    </ThemeContext.Provider>
  );
}

export function useThemeContext() {
  return useContext(ThemeContext);
}
