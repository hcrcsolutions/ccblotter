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
  InfrastructureTopology,
  InfrastructureSummary,
} from '../types';
import {
  isValidAgentArray,
  isValidCallArray,
  isValidAgentSummary,
  isValidSystemStatus,
  isValidQueueMessage,
  isValidInfrastructureTopology,
  safeParseJson,
} from '../lib/typeValidation';
import { isServer, getLocalStorageItem, removeLocalStorageItem } from '../lib/ssr';

const STORAGE_KEY = 'oscc-admin-settings';
const DEFAULT_BACKEND_URL = 'https://localhost:8443';
const MAX_RECONNECT_ATTEMPTS = 5;
const INITIAL_RECONNECT_DELAY = 1000;

function getBackendUrl(): string {
  if (isServer()) return DEFAULT_BACKEND_URL;
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

const initialInfrastructure: InfrastructureTopology = {
  nodes: [],
  edges: [],
  lastUpdated: new Date().toISOString(),
};

const initialInfrastructureSummary: InfrastructureSummary = {
  sipServerCount: 0,
  mediaServerCount: 0,
  totalActiveSessions: 0,
  healthyCount: 0,
  degradedCount: 0,
  unhealthyCount: 0,
};

/**
 * Calculate infrastructure summary from topology.
 */
function calculateInfrastructureSummary(topology: InfrastructureTopology): InfrastructureSummary {
  const nodes = topology.nodes;
  return {
    sipServerCount: nodes.filter(n => n.type === 'SIP').length,
    mediaServerCount: nodes.filter(n => n.type === 'MEDIA').length,
    totalActiveSessions: nodes.reduce((sum, n) => sum + n.activeSessions, 0),
    healthyCount: nodes.filter(n => n.healthStatus === 'HEALTHY').length,
    degradedCount: nodes.filter(n => n.healthStatus === 'DEGRADED').length,
    unhealthyCount: nodes.filter(n => n.healthStatus === 'UNHEALTHY').length,
  };
}

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
  const [infrastructure, setInfrastructure] = useState<InfrastructureTopology>(initialInfrastructure);
  const [infrastructureSummary, setInfrastructureSummary] = useState<InfrastructureSummary>(initialInfrastructureSummary);

  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<StompSubscription[]>([]);
  const reconnectAttempts = useRef(0);
  const reconnectTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const isMountedRef = useRef(true);
  const parseErrorCount = useRef(0);
  const PARSE_ERROR_THRESHOLD = 5; // Trigger disconnect after this many consecutive parse errors

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
      fetch(`${apiUrl}/infrastructure`),
    ]);

    const [agentsResult, callsResult, queueResult, summaryResult, healthResult, infrastructureResult] = results;

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

    // Process infrastructure
    if (infrastructureResult.status === 'fulfilled' && infrastructureResult.value.ok) {
      try {
        const infraData = await infrastructureResult.value.json();
        if (isMountedRef.current && isValidInfrastructureTopology(infraData)) {
          setInfrastructure(infraData);
          setInfrastructureSummary(calculateInfrastructureSummary(infraData));
          console.log(`Loaded ${infraData.nodes.length} infrastructure nodes`);
        }
      } catch (e) {
        console.error('Failed to parse infrastructure response:', e);
      }
    } else if (infrastructureResult.status === 'rejected') {
      console.error('Failed to fetch infrastructure:', infrastructureResult.reason);
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

        // Helper to handle parse errors consistently
        const handleParseResult = <T>(
          data: T | null,
          topic: string,
          onSuccess: (data: T) => void
        ) => {
          if (data) {
            parseErrorCount.current = 0; // Reset on success
            onSuccess(data);
          } else {
            parseErrorCount.current++;
            console.error(`Invalid ${topic} data received from WebSocket (error ${parseErrorCount.current}/${PARSE_ERROR_THRESHOLD})`);
            if (parseErrorCount.current >= PARSE_ERROR_THRESHOLD) {
              console.error('Too many consecutive parse errors, triggering reconnect');
              parseErrorCount.current = 0;
              handleDisconnect();
            }
          }
        };

        // Subscribe to all topics and store references for cleanup
        const agentsSub = client.subscribe('/topic/agents', (message: IMessage) => {
          if (!isMountedRef.current) return;
          const data = safeParseJson(message.body, isValidAgentArray);
          handleParseResult(data, 'agents', setAgents);
        });
        subscriptionsRef.current.push(agentsSub);

        const callsSub = client.subscribe('/topic/calls', (message: IMessage) => {
          if (!isMountedRef.current) return;
          const data = safeParseJson(message.body, isValidCallArray);
          handleParseResult(data, 'calls', setCalls);
        });
        subscriptionsRef.current.push(callsSub);

        const summarySub = client.subscribe('/topic/summary', (message: IMessage) => {
          if (!isMountedRef.current) return;
          const data = safeParseJson(message.body, isValidAgentSummary);
          handleParseResult(data, 'summary', setSummary);
        });
        subscriptionsRef.current.push(summarySub);

        const systemSub = client.subscribe('/topic/system', (message: IMessage) => {
          if (!isMountedRef.current) return;
          const data = safeParseJson(message.body, isValidSystemStatus);
          handleParseResult(data, 'system', setSystemStatus);
        });
        subscriptionsRef.current.push(systemSub);

        const queueSub = client.subscribe('/topic/queue', (message: IMessage) => {
          if (!isMountedRef.current) return;
          const data = safeParseJson(message.body, isValidQueueMessage);
          handleParseResult(data, 'queue', (queueData) => {
            setQueuedCalls(queueData.calls || []);
            setQueueStats(queueData.stats || initialQueueStats);
          });
        });
        subscriptionsRef.current.push(queueSub);

        const infrastructureSub = client.subscribe('/topic/infrastructure', (message: IMessage) => {
          if (!isMountedRef.current) return;
          const data = safeParseJson(message.body, isValidInfrastructureTopology);
          handleParseResult(data, 'infrastructure', (infraData) => {
            setInfrastructure(infraData);
            setInfrastructureSummary(calculateInfrastructureSummary(infraData));
          });
        });
        subscriptionsRef.current.push(infrastructureSub);

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
    infrastructure,
    infrastructureSummary,
    reconnect,
  };
}
