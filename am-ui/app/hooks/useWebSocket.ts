'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type {
  Agent,
  Call,
  QueuedCall,
  QueueStats,
  AgentSummary,
  SystemStatus,
  ConnectionState,
  DashboardState,
} from '../types';

const STORAGE_KEY = 'agent-monitor-settings';
const DEFAULT_BACKEND_URL = 'https://localhost:8443';
const MAX_RECONNECT_ATTEMPTS = 5;
const INITIAL_RECONNECT_DELAY = 1000;

function getBackendUrl(): string {
  if (typeof window === 'undefined') return DEFAULT_BACKEND_URL;
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      const settings = JSON.parse(stored);
      // Handle new format with multiple configs
      if (settings.configs && settings.activeConfigName) {
        const activeConfig = settings.configs.find(
          (c: { name: string; url: string }) => c.name === settings.activeConfigName
        );
        if (activeConfig) {
          return activeConfig.url;
        }
      }
      // Handle old format with just backendUrl
      if (settings.backendUrl) {
        return settings.backendUrl;
      }
    }
  } catch (e) {
    console.error('Failed to load backend URL from settings:', e);
  }
  return DEFAULT_BACKEND_URL;
}

const initialSummary: AgentSummary = {
  online: 0,
  onCall: 0,
  away: 0,
  unavailable: 0,
  total: 0,
};

const initialSystemStatus: SystemStatus = {
  redisConnected: true,
  lastUpdated: new Date().toISOString(),
  errorMessage: null,
};

const initialQueueStats: QueueStats = {
  queuedCount: 0,
  avgWaitSeconds: 0,
  longestWaitSeconds: 0,
};

/**
 * WebSocket hook for real-time dashboard updates.
 *
 * Implements:
 * - STOMP over WebSocket connection
 * - REST API fallback for initial data load
 * - Automatic reconnection with exponential backoff
 * - Fail-closed behavior on Redis unavailability
 */
export function useWebSocket(): DashboardState {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [calls, setCalls] = useState<Call[]>([]);
  const [queuedCalls, setQueuedCalls] = useState<QueuedCall[]>([]);
  const [queueStats, setQueueStats] = useState<QueueStats>(initialQueueStats);
  const [summary, setSummary] = useState<AgentSummary>(initialSummary);
  const [systemStatus, setSystemStatus] = useState<SystemStatus>(initialSystemStatus);
  const [connectionState, setConnectionState] = useState<ConnectionState>('connecting');

  const clientRef = useRef<Client | null>(null);
  const reconnectAttempts = useRef(0);
  const reconnectTimeout = useRef<NodeJS.Timeout | null>(null);

  // Fetch initial data via REST API
  const fetchInitialData = useCallback(async () => {
    try {
      const apiUrl = `${getBackendUrl()}/api`;
      console.log('Fetching initial data via REST API...', apiUrl);

      const [agentsRes, callsRes, queueRes, summaryRes, healthRes] = await Promise.all([
        fetch(`${apiUrl}/agents`),
        fetch(`${apiUrl}/calls`),
        fetch(`${apiUrl}/queue`),
        fetch(`${apiUrl}/agents/summary`),
        fetch(`${apiUrl}/health`),
      ]);

      if (agentsRes.ok) {
        const agentsData = await agentsRes.json();
        setAgents(agentsData);
        console.log(`Loaded ${agentsData.length} agents`);
      }

      if (callsRes.ok) {
        const callsData = await callsRes.json();
        setCalls(callsData);
        console.log(`Loaded ${callsData.length} calls`);
      }

      if (queueRes.ok) {
        const queueData = await queueRes.json();
        setQueuedCalls(queueData.calls || []);
        setQueueStats(queueData.stats || initialQueueStats);
        console.log(`Loaded ${queueData.calls?.length || 0} queued calls`);
      }

      if (summaryRes.ok) {
        const summaryData = await summaryRes.json();
        setSummary(summaryData);
      }

      if (healthRes.ok) {
        const healthData = await healthRes.json();
        setSystemStatus(healthData);
      }
    } catch (error) {
      console.error('Failed to fetch initial data:', error);
    }
  }, []);

  const connect = useCallback(() => {
    // Clean up any existing connection
    if (clientRef.current?.active) {
      clientRef.current.deactivate();
    }

    setConnectionState('connecting');

    const wsUrl = `${getBackendUrl()}/ws`;
    console.log('Connecting to WebSocket:', wsUrl);

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 0, // We handle reconnection manually

      onConnect: () => {
        console.log('WebSocket connected');
        setConnectionState('connected');
        reconnectAttempts.current = 0;

        // Subscribe to all topics
        client.subscribe('/topic/agents', (message: IMessage) => {
          try {
            const data = JSON.parse(message.body) as Agent[];
            setAgents(data);
          } catch (e) {
            console.error('Failed to parse agents message', e);
          }
        });

        client.subscribe('/topic/calls', (message: IMessage) => {
          try {
            const data = JSON.parse(message.body) as Call[];
            setCalls(data);
          } catch (e) {
            console.error('Failed to parse calls message', e);
          }
        });

        client.subscribe('/topic/summary', (message: IMessage) => {
          try {
            const data = JSON.parse(message.body) as AgentSummary;
            setSummary(data);
          } catch (e) {
            console.error('Failed to parse summary message', e);
          }
        });

        client.subscribe('/topic/system', (message: IMessage) => {
          try {
            const data = JSON.parse(message.body) as SystemStatus;
            setSystemStatus(data);
          } catch (e) {
            console.error('Failed to parse system status message', e);
          }
        });

        client.subscribe('/topic/queue', (message: IMessage) => {
          try {
            const data = JSON.parse(message.body) as { calls: QueuedCall[]; stats: QueueStats };
            setQueuedCalls(data.calls || []);
            setQueueStats(data.stats || initialQueueStats);
          } catch (e) {
            console.error('Failed to parse queue message', e);
          }
        });

        // Fetch initial data after subscriptions are set up
        fetchInitialData();
      },

      onDisconnect: () => {
        console.log('WebSocket disconnected');
        handleDisconnect();
      },

      onStompError: (frame) => {
        console.error('STOMP error', frame);
        handleDisconnect();
      },

      onWebSocketError: (event) => {
        console.error('WebSocket error', event);
        handleDisconnect();
      },
    });

    client.activate();
    clientRef.current = client;
  }, [fetchInitialData]);

  const handleDisconnect = useCallback(() => {
    if (reconnectAttempts.current >= MAX_RECONNECT_ATTEMPTS) {
      console.error('Max reconnection attempts reached');
      setConnectionState('error');
      return;
    }

    setConnectionState('disconnected');
    reconnectAttempts.current += 1;

    // Exponential backoff
    const delay = INITIAL_RECONNECT_DELAY * Math.pow(2, reconnectAttempts.current - 1);
    console.log(`Reconnecting in ${delay}ms (attempt ${reconnectAttempts.current})`);

    reconnectTimeout.current = setTimeout(() => {
      connect();
    }, delay);
  }, [connect]);

  useEffect(() => {
    connect();

    return () => {
      if (reconnectTimeout.current) {
        clearTimeout(reconnectTimeout.current);
      }
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
      }
    };
  }, [connect]);

  return {
    agents,
    calls,
    queuedCalls,
    queueStats,
    summary,
    systemStatus,
    connectionState,
  };
}
