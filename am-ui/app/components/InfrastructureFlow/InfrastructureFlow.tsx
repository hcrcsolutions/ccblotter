'use client';

import { useState, useCallback, useMemo, memo } from 'react';
import {
  ReactFlow,
  Controls,
  MiniMap,
  Background,
  BackgroundVariant,
  type NodeMouseHandler,
  type NodeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import BarChartIcon from '@mui/icons-material/BarChart';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import { useThemeContext } from '../../context/ThemeContext';
import { SERVER_HEALTH_COLORS, SERVER_TYPE_COLORS, getStatusColors } from '../../lib/statusColors';
import { TrunkNode } from './TrunkNode';
import { SbcNode } from './SbcNode';
import { SipServerNode } from './SipServerNode';
import { MediaServerNode } from './MediaServerNode';
import { DatacenterHeader } from './DatacenterHeader';
import { ServerDetailDialog } from './ServerDetailDialog';
import { InfrastructureSummaryCards } from './InfrastructureSummaryCards';
import { TopologyFilter } from './TopologyFilter';
import { MetricsPanel } from './MetricsPanel';
import { useAutoLayout } from './useAutoLayout';
import type { InfrastructureTopology, InfrastructureSummary, InfrastructureNode, InfraServerType } from '../../types';

// =============================================================================
// TYPE GUARD
// =============================================================================

const VALID_SERVER_TYPES: InfraServerType[] = ['TRUNK', 'SBC', 'SIP', 'MEDIA'];

/**
 * Type guard to validate that an unknown object is an InfrastructureNode.
 */
function isInfrastructureNode(data: unknown): data is InfrastructureNode {
  if (!data || typeof data !== 'object') return false;
  const obj = data as Record<string, unknown>;
  return (
    typeof obj.id === 'string' &&
    typeof obj.type === 'string' &&
    VALID_SERVER_TYPES.includes(obj.type as InfraServerType) &&
    typeof obj.hostname === 'string' &&
    typeof obj.ipAddress === 'string' &&
    typeof obj.activeSessions === 'number' &&
    typeof obj.maxSessions === 'number' &&
    typeof obj.healthStatus === 'string'
  );
}

// =============================================================================
// TOPOLOGY FILTERING
// =============================================================================
// When a node is pinned, we filter the topology to show only relevant nodes:
// - Pin a Trunk → show that trunk + connected SBCs
// - Pin an SBC → show that SBC + connected trunks + connected SIPs
// - Pin a SIP → show that SIP + connected SBCs + connected media servers
// - Pin a Media → show that media + connected SIPs
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

  // For each node type, determine connected nodes
  switch (pinnedNode.type) {
    case 'TRUNK':
      // Trunk pinned: find connected SBCs (trunk is source)
      topology.edges.forEach(edge => {
        if (edge.sourceId === pinnedNodeId) {
          connectedNodeIds.add(edge.targetId);
        }
      });
      break;

    case 'SBC':
    case 'SIP':
      // SBC/SIP pinned: find all connected nodes (bidirectional)
      // SBC connects to trunks (upstream) and SIPs (downstream)
      // SIP connects to SBCs (upstream) and media servers (downstream)
      topology.edges.forEach(edge => {
        if (edge.targetId === pinnedNodeId) {
          connectedNodeIds.add(edge.sourceId);
        }
        if (edge.sourceId === pinnedNodeId) {
          connectedNodeIds.add(edge.targetId);
        }
      });
      break;

    case 'MEDIA':
      // Media pinned: find connected SIPs (media is target)
      topology.edges.forEach(edge => {
        if (edge.targetId === pinnedNodeId) {
          connectedNodeIds.add(edge.sourceId);
        }
      });
      break;
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

const nodeTypes = {
  trunkServer: TrunkNode,
  sbcServer: SbcNode,
  sipServer: SipServerNode,
  mediaServer: MediaServerNode,
  dcHeader: DatacenterHeader,
} as const satisfies NodeTypes;

interface InfrastructureFlowProps {
  topology: InfrastructureTopology;
  summary: InfrastructureSummary;
  pinnedNodeId?: string | null;
  onPinNode?: (nodeId: string) => void;
  onClearPin?: () => void;
}

export const InfrastructureFlow = memo(function InfrastructureFlow({
  topology,
  summary,
  pinnedNodeId = null,
  onPinNode,
  onClearPin,
}: InfrastructureFlowProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  // State
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [metricsPanelOpen, setMetricsPanelOpen] = useState(false);
  const [groupByDatacenter, setGroupByDatacenter] = useState(true);

  // Derive selected node from current topology to avoid stale data
  const selectedNode = useMemo(() => {
    if (!selectedNodeId) return null;
    return topology.nodes.find(n => n.id === selectedNodeId) ?? null;
  }, [selectedNodeId, topology.nodes]);

  // Filter topology based on pinned node
  const filteredTopology = useMemo(
    () => filterTopologyByPinnedNode(topology, pinnedNodeId),
    [topology, pinnedNodeId]
  );

  const { nodes, edges } = useAutoLayout(filteredTopology, { groupByDatacenter });

  const handleNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    // Ignore clicks on DC header nodes
    if (node.type === 'dcHeader') {
      return;
    }
    // Validate node data conforms to InfrastructureNode before using
    if (isInfrastructureNode(node.data)) {
      setSelectedNodeId(node.data.id);
      setDialogOpen(true);
    }
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

  const handleMetricsPanelClose = useCallback(() => {
    setMetricsPanelOpen(false);
  }, []);

  const handleLayoutToggle = useCallback((_e: React.MouseEvent<HTMLElement>, value: string | null) => {
    if (value !== null) {
      setGroupByDatacenter(value === 'grouped');
    }
  }, []);

  const handleMetricsPanelOpen = useCallback(() => {
    setMetricsPanelOpen(true);
  }, []);

  // Pre-compute MiniMap colors to avoid creating new objects on every node
  const miniMapColors = useMemo(() => ({
    healthy: getStatusColors(SERVER_HEALTH_COLORS.HEALTHY, isDarkMode).text,
    degraded: getStatusColors(SERVER_HEALTH_COLORS.DEGRADED, isDarkMode).text,
    unhealthy: getStatusColors(SERVER_HEALTH_COLORS.UNHEALTHY, isDarkMode).text,
    trunk: getStatusColors(SERVER_TYPE_COLORS.TRUNK, isDarkMode).text,
    sbc: getStatusColors(SERVER_TYPE_COLORS.SBC, isDarkMode).text,
    sip: getStatusColors(SERVER_TYPE_COLORS.SIP, isDarkMode).text,
    media: getStatusColors(SERVER_TYPE_COLORS.MEDIA, isDarkMode).text,
  }), [isDarkMode]);

  // Memoized node color function for MiniMap
  const getNodeColor = useCallback((node: { type?: string; data: unknown }) => {
    // DC header nodes are not shown in minimap
    if (node.type === 'dcHeader') {
      return 'transparent';
    }

    // Safely extract properties with type narrowing
    const data = node.data;
    if (!data || typeof data !== 'object') {
      return miniMapColors.healthy;
    }

    const healthStatus = 'healthStatus' in data ? data.healthStatus : undefined;
    const nodeType = 'type' in data ? data.type : undefined;

    // Color by health first (unhealthy/degraded override type color)
    if (healthStatus === 'UNHEALTHY') {
      return miniMapColors.unhealthy;
    }
    if (healthStatus === 'DEGRADED') {
      return miniMapColors.degraded;
    }

    // Color by type for healthy nodes
    switch (nodeType) {
      case 'TRUNK':
        return miniMapColors.trunk;
      case 'SBC':
        return miniMapColors.sbc;
      case 'SIP':
        return miniMapColors.sip;
      case 'MEDIA':
        return miniMapColors.media;
      default:
        return miniMapColors.healthy;
    }
  }, [miniMapColors]);

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
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Box sx={{ flex: 1 }}>
          <InfrastructureSummaryCards summary={summary} />
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {/* Layout Toggle */}
          <ToggleButtonGroup
            value={groupByDatacenter ? 'grouped' : 'flat'}
            exclusive
            onChange={handleLayoutToggle}
            size="small"
            sx={{
              bgcolor: isDarkMode ? 'hsl(220, 20%, 20%)' : 'hsl(220, 20%, 95%)',
              '& .MuiToggleButton-root': {
                border: 'none',
                px: 1.5,
                '&.Mui-selected': {
                  bgcolor: isDarkMode ? 'hsl(220, 20%, 30%)' : 'hsl(220, 20%, 85%)',
                },
              },
            }}
          >
            <ToggleButton value="grouped">
              <Tooltip title="Group by Datacenter">
                <AccountTreeIcon fontSize="small" />
              </Tooltip>
            </ToggleButton>
            <ToggleButton value="flat">
              <Tooltip title="Flat View">
                <ViewModuleIcon fontSize="small" />
              </Tooltip>
            </ToggleButton>
          </ToggleButtonGroup>

          {/* Metrics Panel Button */}
          <Tooltip title="System Metrics">
            <IconButton
              onClick={handleMetricsPanelOpen}
              sx={{
                bgcolor: isDarkMode ? 'hsl(220, 20%, 20%)' : 'hsl(220, 20%, 95%)',
                '&:hover': {
                  bgcolor: isDarkMode ? 'hsl(220, 20%, 25%)' : 'hsl(220, 20%, 90%)',
                },
              }}
            >
              <BarChartIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>
      <Box sx={{ flex: 1, mt: 2, position: 'relative', minHeight: 0 }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodeClick={handleNodeClick}
          fitView
          fitViewOptions={{ padding: 0.1 }}
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
            nodeColor={getNodeColor}
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
      <MetricsPanel
        open={metricsPanelOpen}
        onClose={handleMetricsPanelClose}
        topology={topology}
        summary={summary}
      />
    </>
  );
});
