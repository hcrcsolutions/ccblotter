'use client';

import * as React from 'react';
import { useState, useCallback } from 'react';
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
import Paper from '@mui/material/Paper';
import DnsIcon from '@mui/icons-material/Dns';
import VideocamIcon from '@mui/icons-material/Videocam';
import { useThemeContext } from '../../context/ThemeContext';
import { SipServerNode } from './SipServerNode';
import { MediaServerNode } from './MediaServerNode';
import { ServerDetailDialog } from './ServerDetailDialog';
import { InfrastructureSummaryCards } from './InfrastructureSummaryCards';
import { useAutoLayout } from './useAutoLayout';
import type { InfrastructureTopology, InfrastructureSummary, InfrastructureNode } from '../../types';

interface LegendItemProps {
  icon?: React.ReactNode;
  color: string;
  bgColor: string;
  label: string;
}

function LegendItem({ icon, color, bgColor, label }: LegendItemProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Box
        sx={{
          width: 24,
          height: 24,
          borderRadius: 1,
          bgcolor: bgColor,
          border: '2px solid',
          borderColor: color,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {icon}
      </Box>
      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
        {label}
      </Typography>
    </Box>
  );
}

function Legend({ isDarkMode }: { isDarkMode: boolean }) {
  return (
    <Paper
      sx={{
        p: 1.5,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
      elevation={2}
    >
      <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.primary', mb: 0.5 }}>
        Server Types
      </Typography>
      <LegendItem
        icon={<DnsIcon sx={{ fontSize: 14, color: isDarkMode ? 'hsl(210, 90%, 70%)' : 'hsl(210, 100%, 35%)' }} />}
        color={isDarkMode ? 'hsl(210, 90%, 70%)' : 'hsl(210, 100%, 35%)'}
        bgColor={isDarkMode ? 'hsl(210, 70%, 25%)' : 'hsl(210, 100%, 92%)'}
        label="SIP Server"
      />
      <LegendItem
        icon={<VideocamIcon sx={{ fontSize: 14, color: isDarkMode ? 'hsl(280, 70%, 70%)' : 'hsl(280, 70%, 35%)' }} />}
        color={isDarkMode ? 'hsl(280, 70%, 70%)' : 'hsl(280, 70%, 35%)'}
        bgColor={isDarkMode ? 'hsl(280, 50%, 25%)' : 'hsl(280, 80%, 92%)'}
        label="Media Server"
      />
      <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.primary', mt: 1, mb: 0.5 }}>
        Health Status
      </Typography>
      <LegendItem
        color={isDarkMode ? 'hsl(120, 50%, 40%)' : 'hsl(120, 60%, 35%)'}
        bgColor={isDarkMode ? 'hsl(120, 40%, 20%)' : 'hsl(120, 60%, 90%)'}
        label="Healthy"
      />
      <LegendItem
        color={isDarkMode ? 'hsl(45, 80%, 45%)' : 'hsl(45, 90%, 35%)'}
        bgColor={isDarkMode ? 'hsl(45, 60%, 20%)' : 'hsl(45, 90%, 90%)'}
        label="Degraded"
      />
      <LegendItem
        color={isDarkMode ? 'hsl(0, 70%, 45%)' : 'hsl(0, 80%, 40%)'}
        bgColor={isDarkMode ? 'hsl(0, 50%, 20%)' : 'hsl(0, 80%, 92%)'}
        label="Unhealthy"
      />
    </Paper>
  );
}

const nodeTypes: NodeTypes = {
  sipServer: SipServerNode,
  mediaServer: MediaServerNode,
};

interface InfrastructureFlowProps {
  topology: InfrastructureTopology;
  summary: InfrastructureSummary;
}

export function InfrastructureFlow({ topology, summary }: InfrastructureFlowProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';
  const { nodes, edges } = useAutoLayout(topology);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [selectedNode, setSelectedNode] = useState<InfrastructureNode | null>(null);

  const handleNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    setSelectedNode(node.data as unknown as InfrastructureNode);
    setDialogOpen(true);
  }, []);

  const handleDialogClose = useCallback(() => {
    setDialogOpen(false);
  }, []);

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
              const healthStatus = (node.data as InfrastructureNode)?.healthStatus;
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
          <Panel position="top-right">
            <Legend isDarkMode={isDarkMode} />
          </Panel>
        </ReactFlow>
      </Box>
      <ServerDetailDialog
        open={dialogOpen}
        server={selectedNode}
        edges={topology.edges}
        onClose={handleDialogClose}
      />
    </>
  );
}
