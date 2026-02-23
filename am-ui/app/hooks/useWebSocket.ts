'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type {
  AgentSummary,
  SystemStatus,
  ConnectionState,
  DashboardState,
  InfrastructureTopology,
  InfrastructureSummary,
} from '../types';
import {
  isValidAgentSummary,
  isValidSystemStatus,
  isValidInfrastructureTopology,
  safeParseJson,
} from '../lib/typeValidation';
import { getBackendUrl, getApiBaseUrl } from '../lib/settings';

const MAX_RECONNECT_ATTEMPTS = 5;
const INITIAL_RECONNECT_DELAY = 1000;

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

const initialInfrastructure: InfrastructureTopology = {
  nodes: [],
  edges: [],
  lastUpdated: new Date().toISOString(),
};

const initialInfrastructureSummary: InfrastructureSummary = {
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
};

/**
 * Calculate infrastructure summary from topology.
 */
function calculateInfrastructureSummary(topology: InfrastructureTopology): InfrastructureSummary {
  const nodes = topology.nodes;

  // Count by type
  const trunkCount = nodes.filter(n => n.type === 'TRUNK').length;
  const sbcCount = nodes.filter(n => n.type === 'SBC').length;
  const sipServerCount = nodes.filter(n => n.type === 'SIP').length;
  const mediaServerCount = nodes.filter(n => n.type === 'MEDIA').length;

  // Health counts (all nodes)
  const healthyCount = nodes.filter(n => n.healthStatus === 'HEALTHY').length;
  const degradedCount = nodes.filter(n => n.healthStatus === 'DEGRADED').length;
  const unhealthyCount = nodes.filter(n => n.healthStatus === 'UNHEALTHY').length;

  // Node health counts (SIP + Media only)
  const sipMediaNodes = nodes.filter(n => n.type === 'SIP' || n.type === 'MEDIA');
  const nodeHealthyCount = sipMediaNodes.filter(n => n.healthStatus === 'HEALTHY').length;
  const nodeDegradedCount = sipMediaNodes.filter(n => n.healthStatus === 'DEGRADED').length;
  const nodeUnhealthyCount = sipMediaNodes.filter(n => n.healthStatus === 'UNHEALTHY').length;

  // Capacity calculations
  const totalActiveSessions = nodes.reduce((sum, n) => sum + n.activeSessions, 0);
  const totalCapacity = nodes.reduce((sum, n) => sum + n.maxSessions, 0);
  const utilizationPercent = totalCapacity > 0 ? (totalActiveSessions / totalCapacity) * 100 : 0;
  const headroomSessions = totalCapacity - totalActiveSessions;

  // Aggregate session breakdown (from nodes that have it)
  const nodesWithBreakdown = nodes.filter(n => n.sessionBreakdown);
  const sessionBreakdown = {
    inboundSessions: nodesWithBreakdown.reduce((sum, n) => sum + (n.sessionBreakdown?.inboundSessions || 0), 0),
    outboundSessions: nodesWithBreakdown.reduce((sum, n) => sum + (n.sessionBreakdown?.outboundSessions || 0), 0),
    ivrSessions: nodesWithBreakdown.reduce((sum, n) => sum + (n.sessionBreakdown?.ivrSessions || 0), 0),
    queueSessions: nodesWithBreakdown.reduce((sum, n) => sum + (n.sessionBreakdown?.queueSessions || 0), 0),
    agentSessions: nodesWithBreakdown.reduce((sum, n) => sum + (n.sessionBreakdown?.agentSessions || 0), 0),
    onHoldSessions: nodesWithBreakdown.reduce((sum, n) => sum + (n.sessionBreakdown?.onHoldSessions || 0), 0),
  };

  // Aggregate metrics (from nodes that have them)
  const nodesWithMetrics = nodes.filter(n => n.metrics);
  const avgLatencyMs = nodesWithMetrics.length > 0
    ? Math.round(nodesWithMetrics.reduce((sum, n) => sum + (n.metrics?.latencyMs || 0), 0) / nodesWithMetrics.length)
    : 0;
  const avgJitterMs = nodesWithMetrics.length > 0
    ? Math.round(nodesWithMetrics.reduce((sum, n) => sum + (n.metrics?.jitterMs || 0), 0) / nodesWithMetrics.length)
    : 0;
  const avgErrorRate = nodesWithMetrics.length > 0
    ? Math.round(nodesWithMetrics.reduce((sum, n) => sum + (n.metrics?.errorRate || 0), 0) / nodesWithMetrics.length * 100) / 100
    : 0;

  // Group by datacenter
  const dcMap = new Map<string, typeof nodes>();
  nodes.forEach(n => {
    const dc = n.datacenter || 'unknown';
    if (!dcMap.has(dc)) {
      dcMap.set(dc, []);
    }
    dcMap.get(dc)!.push(n);
  });

  const datacenterSummaries = Array.from(dcMap.entries()).map(([dcId, dcNodes]) => {
    const dcSessions = dcNodes.reduce((sum, n) => sum + n.activeSessions, 0);
    const dcCapacity = dcNodes.reduce((sum, n) => sum + n.maxSessions, 0);
    return {
      id: dcId,
      region: dcNodes[0]?.region || 'unknown',
      totalNodes: dcNodes.length,
      healthyNodes: dcNodes.filter(n => n.healthStatus === 'HEALTHY').length,
      degradedNodes: dcNodes.filter(n => n.healthStatus === 'DEGRADED').length,
      unhealthyNodes: dcNodes.filter(n => n.healthStatus === 'UNHEALTHY').length,
      totalSessions: dcSessions,
      totalCapacity: dcCapacity,
      utilizationPercent: dcCapacity > 0 ? (dcSessions / dcCapacity) * 100 : 0,
    };
  });

  return {
    sipServerCount,
    mediaServerCount,
    trunkCount,
    sbcCount,
    totalActiveSessions,
    healthyCount,
    degradedCount,
    unhealthyCount,
    nodeHealthyCount,
    nodeDegradedCount,
    nodeUnhealthyCount,
    totalCapacity,
    utilizationPercent,
    headroomSessions,
    sessionBreakdown,
    avgLatencyMs,
    avgJitterMs,
    avgErrorRate,
    datacenterSummaries,
  };
}

/**
 * WebSocket hook for real-time dashboard updates.
 *
 * Subscribes to lightweight topics only: summary, system status, infrastructure.
 * Agents, calls, and queue data are fetched via REST grid endpoints.
 */
export function useWebSocket(): DashboardState {
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

    const apiUrl = getApiBaseUrl();
    console.log('Fetching initial data via REST API...', apiUrl);

    const results = await Promise.allSettled([
      fetch(`${apiUrl}/agents/summary`),
      fetch(`${apiUrl}/health`),
      fetch(`${apiUrl}/infrastructure`),
    ]);

    const [summaryResult, healthResult, infrastructureResult] = results;

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
        const handleParseResult = <T,>(
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

        // Subscribe to lightweight topics only (summary, system, infrastructure)
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
    summary,
    systemStatus,
    connectionState,
    infrastructure,
    infrastructureSummary,
    reconnect,
  };
}
