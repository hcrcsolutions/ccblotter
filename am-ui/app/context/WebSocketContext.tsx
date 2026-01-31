'use client';

import * as React from 'react';
import { createContext, useContext } from 'react';
import { useWebSocket } from '../hooks/useWebSocket';
import type { DashboardState } from '../types';

const initialState: DashboardState = {
  agents: [],
  calls: [],
  queuedCalls: [],
  queueStats: {
    queuedCount: 0,
    avgWaitSeconds: 0,
    longestWaitSeconds: 0,
  },
  summary: {
    online: 0,
    onCall: 0,
    away: 0,
    unavailable: 0,
    total: 0,
  },
  systemStatus: {
    redisConnected: true,
    lastUpdated: new Date().toISOString(),
    errorMessage: null,
  },
  connectionState: 'connecting',
  infrastructure: {
    nodes: [],
    edges: [],
    lastUpdated: new Date().toISOString(),
  },
  infrastructureSummary: {
    sipServerCount: 0,
    mediaServerCount: 0,
    totalActiveSessions: 0,
    healthyCount: 0,
    degradedCount: 0,
    unhealthyCount: 0,
  },
  reconnect: () => {},
};

const WebSocketContext = createContext<DashboardState>(initialState);

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const state = useWebSocket();
  return (
    <WebSocketContext.Provider value={state}>
      {children}
    </WebSocketContext.Provider>
  );
}

export function useWebSocketContext(): DashboardState {
  return useContext(WebSocketContext);
}
