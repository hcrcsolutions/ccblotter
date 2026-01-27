'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type {
  Agent,
  Call,
  AgentSummary,
  SystemStatus,
  ConnectionState,
  DashboardState,
} from '../types';

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/ws';
const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
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
  const [summary, setSummary] = useState<AgentSummary>(initialSummary);
  const [systemStatus, setSystemStatus] = useState<SystemStatus>(initialSystemStatus);
  const [connectionState, setConnectionState] = useState<ConnectionState>('connecting');

  const clientRef = useRef<Client | null>(null);
  const reconnectAttempts = useRef(0);
  const reconnectTimeout = useRef<NodeJS.Timeout | null>(null);

  // Fetch initial data via REST API
  const fetchInitialData = useCallback(async () => {
    try {
      console.log('Fetching initial data via REST API...');

      const [agentsRes, callsRes, summaryRes, healthRes] = await Promise.all([
        fetch(`${API_URL}/agents`),
        fetch(`${API_URL}/calls`),
        fetch(`${API_URL}/agents/summary`),
        fetch(`${API_URL}/health`),
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

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
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
    summary,
    systemStatus,
    connectionState,
  };
}
