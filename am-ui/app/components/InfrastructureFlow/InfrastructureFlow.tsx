'use client';

import * as React from 'react';
import { useState, useCallback, useMemo } from 'react';
import {
  ReactFlow,
  Controls,
  MiniMap,
  Background,
  BackgroundVariant,
  Panel,
  type NodeMouseHandler,
  type NodeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useThemeContext } from '../../context/ThemeContext';
import { SipServerNode } from './SipServerNode';
import { MediaServerNode } from './MediaServerNode';
import { ServerDetailDialog } from './ServerDetailDialog';
import { InfrastructureSummaryCards } from './InfrastructureSummaryCards';
import { TopologyFilter } from './TopologyFilter';
import { useAutoLayout } from './useAutoLayout';
import type { InfrastructureTopology, InfrastructureSummary, InfrastructureNode } from '../../types';

// =============================================================================
// TOPOLOGY FILTERING
// =============================================================================
// When a node is pinned, we filter the topology to show only relevant nodes:
// - Pin a SIP server → show that SIP + all its connected media servers
// - Pin a Media server → show that media + all SIPs connected to it
// =============================================================================

function filterTopologyByPinnedNode(
  topology: InfrastructureTopology,
  pinnedNodeId: string | null
): InfrastructureTopology {
  if (!pinnedNodeId) {
    return topology;
  }

  const pinnedNode = topology.nodes.find(n => n.id === pinnedNodeId);
  if (!pinnedNode) {
    return topology;
  }

  // Find all connected node IDs based on edges
  const connectedNodeIds = new Set<string>([pinnedNodeId]);

  if (pinnedNode.type === 'SIP') {
    // SIP pinned: find all media servers connected to this SIP
    topology.edges.forEach(edge => {
      if (edge.sourceId === pinnedNodeId) {
        connectedNodeIds.add(edge.targetId);
      }
    });
  } else {
    // Media pinned: find all SIP servers connected to this media
    topology.edges.forEach(edge => {
      if (edge.targetId === pinnedNodeId) {
        connectedNodeIds.add(edge.sourceId);
      }
    });
  }

  // Filter nodes and edges
  const filteredNodes = topology.nodes.filter(n => connectedNodeIds.has(n.id));
  const filteredEdges = topology.edges.filter(
    e => connectedNodeIds.has(e.sourceId) && connectedNodeIds.has(e.targetId)
  );

  return {
    nodes: filteredNodes,
    edges: filteredEdges,
    lastUpdated: topology.lastUpdated,
  };
}

const nodeTypes: NodeTypes = {
  sipServer: SipServerNode,
  mediaServer: MediaServerNode,
};

interface InfrastructureFlowProps {
  topology: InfrastructureTopology;
  summary: InfrastructureSummary;
  pinnedNodeId?: string | null;
  onPinNode?: (nodeId: string) => void;
  onClearPin?: () => void;
}

export function InfrastructureFlow({
  topology,
  summary,
  pinnedNodeId = null,
  onPinNode,
  onClearPin,
}: InfrastructureFlowProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  // Filter topology based on pinned node
  const filteredTopology = useMemo(
    () => filterTopologyByPinnedNode(topology, pinnedNodeId),
    [topology, pinnedNodeId]
  );

  const { nodes, edges } = useAutoLayout(filteredTopology);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedNode, setSelectedNode] = useState<InfrastructureNode | null>(null);

  const handleNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    setSelectedNode(node.data as unknown as InfrastructureNode);
    setDialogOpen(true);
  }, []);

  const handleDialogClose = useCallback(() => {
    setDialogOpen(false);
  }, []);

  const handlePinNode = useCallback(() => {
    if (selectedNode && onPinNode) {
      onPinNode(selectedNode.id);
      setDialogOpen(false);
    }
  }, [selectedNode, onPinNode]);

  if (nodes.length === 0) {
    return (
      <>
        <InfrastructureSummaryCards summary={summary} />
        <Box
          sx={{
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mt: 3,
          }}
        >
          <Typography variant="h6" color="text.secondary">
            No infrastructure nodes available
          </Typography>
        </Box>
      </>
    );
  }

  return (
    <>
      <InfrastructureSummaryCards summary={summary} />
      <Box sx={{ flex: 1, mt: 3, position: 'relative', minHeight: 0 }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodeClick={handleNodeClick}
          fitView
          fitViewOptions={{ padding: 0.2 }}
          proOptions={{ hideAttribution: true }}
          style={{
            backgroundColor: isDarkMode ? 'hsl(220, 20%, 10%)' : 'hsl(220, 20%, 98%)',
          }}
        >
          <Controls
            style={{
              backgroundColor: isDarkMode ? 'hsl(220, 20%, 15%)' : 'white',
              borderColor: isDarkMode ? 'hsl(220, 20%, 30%)' : 'hsl(220, 20%, 85%)',
            }}
          />
          <MiniMap
            position="bottom-right"
            style={{
              backgroundColor: isDarkMode ? 'hsl(220, 20%, 15%)' : 'white',
            }}
            maskColor={isDarkMode ? 'rgba(0, 0, 0, 0.6)' : 'rgba(255, 255, 255, 0.6)'}
            nodeColor={(node) => {
              const healthStatus = (node.data as unknown as InfrastructureNode)?.healthStatus;
              if (healthStatus === 'UNHEALTHY') {
                return isDarkMode ? 'hsl(0, 70%, 45%)' : 'hsl(0, 80%, 50%)';
              }
              if (healthStatus === 'DEGRADED') {
                return isDarkMode ? 'hsl(45, 80%, 45%)' : 'hsl(45, 90%, 45%)';
              }
              // HEALTHY or UNKNOWN - use green
              return isDarkMode ? 'hsl(120, 50%, 40%)' : 'hsl(120, 60%, 45%)';
            }}
            nodeStrokeWidth={3}
            pannable
            zoomable
          />
          <Background
            variant={BackgroundVariant.Dots}
            color={isDarkMode ? 'hsl(220, 20%, 25%)' : 'hsl(220, 20%, 85%)'}
            gap={20}
            size={1}
          />
        </ReactFlow>
        {/* Filter Panel */}
        {onPinNode && onClearPin && (
          <TopologyFilter
            nodes={topology.nodes}
            pinnedNodeId={pinnedNodeId}
            onPinNode={onPinNode}
            onUnpin={onClearPin}
          />
        )}
      </Box>
      <ServerDetailDialog
        open={dialogOpen}
        server={selectedNode}
        edges={topology.edges}
        onClose={handleDialogClose}
        onPin={onPinNode ? handlePinNode : undefined}
        isPinned={selectedNode?.id === pinnedNodeId}
      />
    </>
  );
}
