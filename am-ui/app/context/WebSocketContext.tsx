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
    trunkCount: 0,
    sbcCount: 0,
    totalActiveSessions: 0,
    healthyCount: 0,
    degradedCount: 0,
    unhealthyCount: 0,
    nodeHealthyCount: 0,
    nodeDegradedCount: 0,
    nodeUnhealthyCount: 0,
    totalCapacity: 0,
    utilizationPercent: 0,
    headroomSessions: 0,
    sessionBreakdown: {
      inboundSessions: 0,
      outboundSessions: 0,
      ivrSessions: 0,
      queueSessions: 0,
      agentSessions: 0,
      onHoldSessions: 0,
    },
    avgLatencyMs: 0,
    avgJitterMs: 0,
    avgErrorRate: 0,
    datacenterSummaries: [],
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
