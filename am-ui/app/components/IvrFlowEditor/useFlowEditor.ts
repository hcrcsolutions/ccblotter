import * as React from 'react';
import {
  type Node,
  type Edge,
  type Connection,
  type NodeChange,
  type EdgeChange,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
  reconnectEdge,
} from '@xyflow/react';
import { getBackendUrl } from '../../lib/settings';
import { getDefaultConfig, getNodeLabel } from './nodeDefaults';
import type { IvrNodeType, IvrFlowContent, IvrPublishResult } from '../../types';

interface FlowEditorState {
  nodes: Node[];
  edges: Edge[];
  selectedNodeId: string | null;
  saving: boolean;
  publishing: boolean;
  error: string | null;
  snackbar: string | null;
  publishResult: IvrPublishResult | null;
  dirty: boolean;
}

export function useFlowEditor(flowId: string) {
  const [state, setState] = React.useState<FlowEditorState>({
    nodes: [],
    edges: [],
    selectedNodeId: null,
    saving: false,
    publishing: false,
    error: null,
    snackbar: null,
    publishResult: null,
    dirty: false,
  });

  const nodeIdCounter = React.useRef(0);

  // Load flow content on mount
  React.useEffect(() => {
    async function loadContent() {
      try {
        const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows/${flowId}/content`);
        if (!res.ok) {
          throw new Error('Failed to load flow content');
        }
        const data: IvrFlowContent = await res.json();
        const parsed = typeof data.content === 'string' ? JSON.parse(data.content) : data.content;
        const rfNodes: Node[] = (parsed.nodes || []).map((n: Record<string, unknown>) => ({
          id: n.id as string,
          type: 'ivrNode',
          position: n.position as { x: number; y: number } || { x: 0, y: 0 },
          data: { ...n },
        }));
        const rfEdges: Edge[] = (parsed.edges || []).map((e: Record<string, unknown>) => ({
          id: e.id as string,
          source: e.sourceNodeId as string,
          target: e.targetNodeId as string,
          label: (e.condition as string) || undefined,
          type: 'ivrEdge',
        }));

        // Track the highest numeric suffix to prevent ID collisions
        let maxId = 0;
        rfNodes.forEach((n: Node) => {
          const match = n.id.match(/(\d+)$/);
          if (match) {
            maxId = Math.max(maxId, parseInt(match[1], 10));
          }
        });
        nodeIdCounter.current = maxId;

        setState((prev) => ({ ...prev, nodes: rfNodes, edges: rfEdges }));
      } catch (e) {
        setState((prev) => ({
          ...prev,
          error: e instanceof Error ? e.message : 'Failed to load content',
        }));
      }
    }
    loadContent();
  }, [flowId]);

  const onNodesChange = React.useCallback((changes: NodeChange[]) => {
    setState((prev) => ({
      ...prev,
      nodes: applyNodeChanges(changes, prev.nodes),
      dirty: true,
    }));
  }, []);

  const onEdgesChange = React.useCallback((changes: EdgeChange[]) => {
    setState((prev) => ({
      ...prev,
      edges: applyEdgeChanges(changes, prev.edges),
      dirty: true,
    }));
  }, []);

  const onConnect = React.useCallback((connection: Connection) => {
    setState((prev) => ({
      ...prev,
      edges: addEdge({ ...connection, id: `e-${Date.now()}`, type: 'ivrEdge' }, prev.edges),
      dirty: true,
    }));
  }, []);

  const onReconnect = React.useCallback((oldEdge: Edge, newConnection: Connection) => {
    setState((prev) => ({
      ...prev,
      edges: reconnectEdge(oldEdge, newConnection, prev.edges),
      dirty: true,
    }));
  }, []);

  const addNode = React.useCallback((type: IvrNodeType, position: { x: number; y: number }) => {
    nodeIdCounter.current += 1;
    const id = `node-${nodeIdCounter.current}`;
    const newNode: Node = {
      id,
      type: 'ivrNode',
      position,
      data: {
        id,
        type,
        label: getNodeLabel(type),
        config: getDefaultConfig(type),
      },
    };
    setState((prev) => ({
      ...prev,
      nodes: [...prev.nodes, newNode],
      selectedNodeId: id,
      dirty: true,
    }));
  }, []);

  const updateNodeData = React.useCallback((nodeId: string, data: Record<string, unknown>) => {
    setState((prev) => ({
      ...prev,
      nodes: prev.nodes.map((n) =>
        n.id === nodeId ? { ...n, data: { ...n.data, ...data } } : n
      ),
      dirty: true,
    }));
  }, []);

  const deleteNode = React.useCallback((nodeId: string) => {
    setState((prev) => ({
      ...prev,
      nodes: prev.nodes.filter((n) => n.id !== nodeId),
      edges: prev.edges.filter((e) => e.source !== nodeId && e.target !== nodeId),
      selectedNodeId: prev.selectedNodeId === nodeId ? null : prev.selectedNodeId,
      dirty: true,
    }));
  }, []);

  const selectNode = React.useCallback((nodeId: string | null) => {
    setState((prev) => ({ ...prev, selectedNodeId: nodeId }));
  }, []);

  const save = React.useCallback(async () => {
    setState((prev) => ({ ...prev, saving: true, error: null }));
    try {
      const nodes = state.nodes.map((n) => ({
        id: n.id,
        type: (n.data as Record<string, unknown>).type,
        label: (n.data as Record<string, unknown>).label,
        position: n.position,
        config: (n.data as Record<string, unknown>).config,
      }));
      const edges = state.edges.map((e) => ({
        id: e.id,
        sourceNodeId: e.source,
        targetNodeId: e.target,
        condition: e.label || null,
      }));

      const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows/${flowId}/content`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nodes, edges, variables: [] }),
      });
      if (!res.ok) {
        throw new Error('Failed to save flow');
      }
      setState((prev) => ({ ...prev, saving: false, dirty: false, snackbar: 'Flow saved' }));
    } catch (e) {
      setState((prev) => ({
        ...prev,
        saving: false,
        error: e instanceof Error ? e.message : 'Failed to save',
      }));
    }
  }, [flowId, state.nodes, state.edges]);

  const publish = React.useCallback(async () => {
    // Save first if dirty
    if (state.dirty) {
      await save();
    }
    setState((prev) => ({ ...prev, publishing: true, error: null }));
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows/${flowId}/publish`, {
        method: 'POST',
      });
      const result: IvrPublishResult = await res.json();
      setState((prev) => ({
        ...prev,
        publishing: false,
        publishResult: result,
        snackbar: result.published ? 'Flow published' : null,
      }));
    } catch (e) {
      setState((prev) => ({
        ...prev,
        publishing: false,
        error: e instanceof Error ? e.message : 'Failed to publish',
      }));
    }
  }, [flowId, state.dirty, save]);

  const clearSnackbar = React.useCallback(() => {
    setState((prev) => ({ ...prev, snackbar: null }));
  }, []);

  const clearPublishResult = React.useCallback(() => {
    setState((prev) => ({ ...prev, publishResult: null }));
  }, []);

  const clearError = React.useCallback(() => {
    setState((prev) => ({ ...prev, error: null }));
  }, []);

  return {
    ...state,
    onNodesChange,
    onEdgesChange,
    onConnect,
    onReconnect,
    addNode,
    updateNodeData,
    deleteNode,
    selectNode,
    save,
    publish,
    clearSnackbar,
    clearPublishResult,
    clearError,
  };
}
