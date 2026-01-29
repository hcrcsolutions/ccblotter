'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
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

const STORAGE_KEY = 'oscc-admin-settings';
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
  const subscriptionsRef = useRef<StompSubscription[]>([]);
  const reconnectAttempts = useRef(0);
  const reconnectTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isMountedRef = useRef(true);

  // Fetch initial data via REST API using Promise.allSettled for resilience
  const fetchInitialData = useCallback(async () => {
    if (!isMountedRef.current) return;

    const apiUrl = `${getBackendUrl()}/api`;
    console.log('Fetching initial data via REST API...', apiUrl);

    const results = await Promise.allSettled([
      fetch(`${apiUrl}/agents`),
      fetch(`${apiUrl}/calls`),
      fetch(`${apiUrl}/queue`),
      fetch(`${apiUrl}/agents/summary`),
      fetch(`${apiUrl}/health`),
    ]);

    const [agentsResult, callsResult, queueResult, summaryResult, healthResult] = results;

    // Process agents
    if (agentsResult.status === 'fulfilled' && agentsResult.value.ok) {
      try {
        const agentsData = await agentsResult.value.json();
        if (isMountedRef.current) {
          setAgents(agentsData);
          console.log(`Loaded ${agentsData.length} agents`);
        }
      } catch (e) {
        console.error('Failed to parse agents response:', e);
      }
    } else if (agentsResult.status === 'rejected') {
      console.error('Failed to fetch agents:', agentsResult.reason);
    }

    // Process calls
    if (callsResult.status === 'fulfilled' && callsResult.value.ok) {
      try {
        const callsData = await callsResult.value.json();
        if (isMountedRef.current) {
          setCalls(callsData);
          console.log(`Loaded ${callsData.length} calls`);
        }
      } catch (e) {
        console.error('Failed to parse calls response:', e);
      }
    } else if (callsResult.status === 'rejected') {
      console.error('Failed to fetch calls:', callsResult.reason);
    }

    // Process queue
    if (queueResult.status === 'fulfilled' && queueResult.value.ok) {
      try {
        const queueData = await queueResult.value.json();
        if (isMountedRef.current) {
          setQueuedCalls(queueData.calls || []);
          setQueueStats(queueData.stats || initialQueueStats);
          console.log(`Loaded ${queueData.calls?.length || 0} queued calls`);
        }
      } catch (e) {
        console.error('Failed to parse queue response:', e);
      }
    } else if (queueResult.status === 'rejected') {
      console.error('Failed to fetch queue:', queueResult.reason);
    }

    // Process summary
    if (summaryResult.status === 'fulfilled' && summaryResult.value.ok) {
      try {
        const summaryData = await summaryResult.value.json();
        if (isMountedRef.current) {
          setSummary(summaryData);
        }
      } catch (e) {
        console.error('Failed to parse summary response:', e);
      }
    } else if (summaryResult.status === 'rejected') {
      console.error('Failed to fetch summary:', summaryResult.reason);
    }

    // Process health
    if (healthResult.status === 'fulfilled' && healthResult.value.ok) {
      try {
        const healthData = await healthResult.value.json();
        if (isMountedRef.current) {
          setSystemStatus(healthData);
        }
      } catch (e) {
        console.error('Failed to parse health response:', e);
      }
    } else if (healthResult.status === 'rejected') {
      console.error('Failed to fetch health:', healthResult.reason);
    }
  }, []);

  const connect = useCallback(() => {
    // Don't connect if component is unmounted
    if (!isMountedRef.current) {
      return;
    }

    // Unsubscribe from all existing subscriptions to prevent memory leak
    subscriptionsRef.current.forEach(subscription => {
      try {
        subscription.unsubscribe();
      } catch (e) {
        // Subscription may already be invalid if client disconnected
      }
    });
    subscriptionsRef.current = [];

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
        if (!isMountedRef.current) return;

        console.log('WebSocket connected');
        setConnectionState('connected');
        reconnectAttempts.current = 0;

        // Subscribe to all topics and store references for cleanup
        const agentsSub = client.subscribe('/topic/agents', (message: IMessage) => {
          if (!isMountedRef.current) return;
          try {
            const data = JSON.parse(message.body) as Agent[];
            setAgents(data);
          } catch (e) {
            console.error('Failed to parse agents message', e);
          }
        });
        subscriptionsRef.current.push(agentsSub);

        const callsSub = client.subscribe('/topic/calls', (message: IMessage) => {
          if (!isMountedRef.current) return;
          try {
            const data = JSON.parse(message.body) as Call[];
            setCalls(data);
          } catch (e) {
            console.error('Failed to parse calls message', e);
          }
        });
        subscriptionsRef.current.push(callsSub);

        const summarySub = client.subscribe('/topic/summary', (message: IMessage) => {
          if (!isMountedRef.current) return;
          try {
            const data = JSON.parse(message.body) as AgentSummary;
            setSummary(data);
          } catch (e) {
            console.error('Failed to parse summary message', e);
          }
        });
        subscriptionsRef.current.push(summarySub);

        const systemSub = client.subscribe('/topic/system', (message: IMessage) => {
          if (!isMountedRef.current) return;
          try {
            const data = JSON.parse(message.body) as SystemStatus;
            setSystemStatus(data);
          } catch (e) {
            console.error('Failed to parse system status message', e);
          }
        });
        subscriptionsRef.current.push(systemSub);

        const queueSub = client.subscribe('/topic/queue', (message: IMessage) => {
          if (!isMountedRef.current) return;
          try {
            const data = JSON.parse(message.body) as { calls: QueuedCall[]; stats: QueueStats };
            setQueuedCalls(data.calls || []);
            setQueueStats(data.stats || initialQueueStats);
          } catch (e) {
            console.error('Failed to parse queue message', e);
          }
        });
        subscriptionsRef.current.push(queueSub);

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
    // Don't attempt reconnection if component is unmounted
    if (!isMountedRef.current) {
      return;
    }

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
      if (isMountedRef.current) {
        connect();
      }
    }, delay);
  }, [connect]);

  useEffect(() => {
    isMountedRef.current = true;
    connect();

    return () => {
      // Mark as unmounted first to prevent reconnection attempts and state updates
      isMountedRef.current = false;

      // Clear any pending reconnection timeout
      if (reconnectTimeout.current) {
        clearTimeout(reconnectTimeout.current);
        reconnectTimeout.current = null;
      }

      // Unsubscribe from all topics before deactivating
      subscriptionsRef.current.forEach(subscription => {
        try {
          subscription.unsubscribe();
        } catch (e) {
          // Subscription may already be invalid
        }
      });
      subscriptionsRef.current = [];

      // Deactivate the client
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
      }
      clientRef.current = null;
    };
  }, [connect]);

  const reconnect = useCallback(() => {
    console.log('Manual reconnect triggered');
    reconnectAttempts.current = 0;
    if (reconnectTimeout.current) {
      clearTimeout(reconnectTimeout.current);
    }
    connect();
  }, [connect]);

  return {
    agents,
    calls,
    queuedCalls,
    queueStats,
    summary,
    systemStatus,
    connectionState,
    reconnect,
  };
}
