'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueFormatterParams } from 'ag-grid-community';
import { AllCommunityModule, ModuleRegistry, themeQuartz } from 'ag-grid-community';
import Chip from '@mui/material/Chip';
import QueueIcon from '@mui/icons-material/Queue';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import WarningIcon from '@mui/icons-material/Warning';
import { useWebSocket } from '../../hooks/useWebSocket';
import { ConnectionStatus } from '../../components/ConnectionStatus/ConnectionStatus';
import { ErrorBanner } from '../../components/ErrorBanner/ErrorBanner';
import type { Call, QueuedCall, CallState } from '../../types';

// Register AG Grid modules
ModuleRegistry.registerModules([AllCommunityModule]);

// Status colors for call state
const CALL_STATE_COLORS: Record<CallState, {
  light: { bg: string; text: string };
  dark: { bg: string; text: string };
  label: string;
}> = {
  TALKING: {
    light: { bg: 'hsl(120, 80%, 92%)', text: 'hsl(120, 59%, 30%)' },
    dark: { bg: 'hsl(120, 50%, 20%)', text: 'hsl(120, 70%, 65%)' },
    label: 'Talking',
  },
  ON_HOLD: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(45, 90%, 35%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 100%, 65%)' },
    label: 'On Hold',
  },
};

// Priority colors
const PRIORITY_COLORS: Record<number, {
  light: { bg: string; text: string };
  dark: { bg: string; text: string };
  label: string;
}> = {
  1: {
    light: { bg: 'hsl(0, 100%, 92%)', text: 'hsl(0, 90%, 40%)' },
    dark: { bg: 'hsl(0, 50%, 22%)', text: 'hsl(0, 90%, 70%)' },
    label: 'High',
  },
  2: {
    light: { bg: 'hsl(45, 100%, 90%)', text: 'hsl(45, 90%, 35%)' },
    dark: { bg: 'hsl(45, 60%, 22%)', text: 'hsl(45, 100%, 65%)' },
    label: 'Medium',
  },
  3: {
    light: { bg: 'hsl(210, 100%, 92%)', text: 'hsl(210, 98%, 42%)' },
    dark: { bg: 'hsl(210, 60%, 25%)', text: 'hsl(210, 100%, 70%)' },
    label: 'Normal',
  },
};

function formatDuration(seconds: number | null): string {
  if (seconds == null || seconds < 0) return '-';
  const minutes = Math.floor(seconds / 60);
  const secs = seconds % 60;
  if (minutes > 0) {
    return `${minutes}m ${secs}s`;
  }
  return `${secs}s`;
}

function calculateDuration(timestamp: string | null): number | null {
  if (!timestamp) return null;
  const start = new Date(timestamp).getTime();
  const now = Date.now();
  return Math.floor((now - start) / 1000);
}

// Summary card component
interface SummaryCardProps {
  title: string;
  value: string | number;
  icon: React.ReactNode;
  color: string;
  bgColor: string;
}

function SummaryCard({ title, value, icon, color, bgColor }: SummaryCardProps) {
  return (
    <Card sx={{ width: 200 }}>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
              {title}
            </Typography>
            <Typography variant="h3" component="div" sx={{ fontWeight: 600, color }}>
              {value}
            </Typography>
          </Box>
          <Box
            sx={{
              width: 56,
              height: 56,
              borderRadius: 2,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: bgColor,
              color,
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}

// Call state cell renderer
function CallStateCellRenderer(params: ICellRendererParams) {
  const state = params.value as CallState;
  if (!state) return null;
  const config = CALL_STATE_COLORS[state];
  const isDark = params.context?.isDarkMode;
  const colors = isDark ? config.dark : config.light;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
      <Chip
        label={config.label}
        size="small"
        sx={{
          backgroundColor: colors.bg,
          color: colors.text,
          fontWeight: 600,
          fontSize: '0.75rem',
          height: 24,
          border: '1px solid',
          borderColor: colors.text,
        }}
      />
    </Box>
  );
}

// Priority cell renderer
function PriorityCellRenderer(params: ICellRendererParams) {
  const priority = params.value as number;
  const config = PRIORITY_COLORS[priority] || PRIORITY_COLORS[3];
  const isDark = params.context?.isDarkMode;
  const colors = isDark ? config.dark : config.light;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
      <Chip
        label={config.label}
        size="small"
        sx={{
          backgroundColor: colors.bg,
          color: colors.text,
          fontWeight: 600,
          fontSize: '0.75rem',
          height: 24,
          border: '1px solid',
          borderColor: colors.text,
        }}
      />
    </Box>
  );
}

// Wait time cell renderer with color coding
function WaitTimeCellRenderer(params: ICellRendererParams) {
  const waitSeconds = params.value as number | null;
  const isDark = params.context?.isDarkMode;

  if (waitSeconds == null) {
    return <Typography variant="body2" sx={{ color: 'text.secondary' }}>-</Typography>;
  }

  // Color coding: >60s = warning, >120s = alert
  let color = isDark ? 'hsl(120, 70%, 65%)' : 'hsl(120, 59%, 30%)';
  if (waitSeconds > 120) {
    color = isDark ? 'hsl(0, 90%, 70%)' : 'hsl(0, 90%, 40%)';
  } else if (waitSeconds > 60) {
    color = isDark ? 'hsl(45, 100%, 65%)' : 'hsl(45, 90%, 35%)';
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
      <Typography variant="body2" sx={{ color, fontWeight: waitSeconds > 60 ? 600 : 400 }}>
        {formatDuration(waitSeconds)}
      </Typography>
    </Box>
  );
}

export default function CallsPage() {
  const theme = useTheme();
  const { calls, queuedCalls, queueStats, systemStatus, connectionState } = useWebSocket();

  const queuedGridRef = React.useRef<AgGridReact>(null);
  const activeGridRef = React.useRef<AgGridReact>(null);

  const [queuedRowData, setQueuedRowData] = React.useState<Array<{
    id: string;
    originator: string;
    waitTime: number;
    priority: number;
    skill: string;
    position: number;
  }>>([]);

  const [activeRowData, setActiveRowData] = React.useState<Array<{
    id: string;
    originator: string;
    agentName: string;
    duration: number;
    state: CallState;
  }>>([]);

  // Update queued calls data
  React.useEffect(() => {
    const updateData = () => {
      const rows = queuedCalls.map((call, index) => ({
        id: call.id,
        originator: call.originator,
        waitTime: calculateDuration(call.queuedAt) || 0,
        priority: call.priority,
        skill: call.skill,
        position: index + 1,
      }));
      setQueuedRowData(rows);
    };

    updateData();
    const interval = setInterval(updateData, 1000);
    return () => clearInterval(interval);
  }, [queuedCalls]);

  // Update active calls data
  React.useEffect(() => {
    const updateData = () => {
      const rows = calls.map(call => ({
        id: call.id,
        originator: call.originator,
        agentName: call.agentName,
        duration: calculateDuration(call.startTime) || 0,
        state: call.state,
      }));
      setActiveRowData(rows);
    };

    updateData();
    const interval = setInterval(updateData, 1000);
    return () => clearInterval(interval);
  }, [calls]);

  // Queued calls column definitions
  const queuedColumnDefs = React.useMemo<ColDef[]>(() => [
    { field: 'position', headerName: '#', width: 60, sortable: true },
    { field: 'originator', headerName: 'Caller', width: 140, filter: 'agTextColumnFilter', sortable: true },
    { field: 'skill', headerName: 'Skill', width: 100, filter: 'agTextColumnFilter', sortable: true },
    { field: 'priority', headerName: 'Priority', width: 100, cellRenderer: PriorityCellRenderer, sortable: true },
    {
      field: 'waitTime',
      headerName: 'Wait Time',
      width: 110,
      cellRenderer: WaitTimeCellRenderer,
      sortable: true,
    },
  ], []);

  // Active calls column definitions
  const activeColumnDefs = React.useMemo<ColDef[]>(() => [
    { field: 'originator', headerName: 'Caller', width: 140, filter: 'agTextColumnFilter', sortable: true },
    { field: 'agentName', headerName: 'Agent', width: 160, filter: 'agTextColumnFilter', sortable: true },
    { field: 'state', headerName: 'State', width: 100, cellRenderer: CallStateCellRenderer, sortable: true },
    {
      field: 'duration',
      headerName: 'Duration',
      width: 100,
      valueFormatter: (params: ValueFormatterParams) => formatDuration(params.value),
      sortable: true,
    },
  ], []);

  const defaultColDef = React.useMemo<ColDef>(() => ({
    resizable: true,
  }), []);

  // Grid theme
  const gridTheme = React.useMemo(() => {
    return themeQuartz.withParams({
      backgroundColor: theme.palette.background.paper,
      foregroundColor: theme.palette.text.primary,
      headerBackgroundColor: theme.palette.background.default,
      headerTextColor: theme.palette.text.primary,
      oddRowBackgroundColor: theme.palette.action.hover,
      borderColor: theme.palette.divider,
      fontFamily: theme.typography.fontFamily,
      fontSize: 14,
      headerFontSize: 14,
      rowHeight: 44,
      headerHeight: 40,
    });
  }, [theme]);

  const gridContext = React.useMemo(() => ({
    isDarkMode: theme.palette.mode === 'dark',
  }), [theme.palette.mode]);

  // Refresh cells on theme change
  const prevModeRef = React.useRef(theme.palette.mode);
  React.useEffect(() => {
    if (prevModeRef.current !== theme.palette.mode) {
      prevModeRef.current = theme.palette.mode;
      setTimeout(() => {
        queuedGridRef.current?.api?.refreshCells({ force: true });
        activeGridRef.current?.api?.refreshCells({ force: true });
      }, 0);
    }
  }, [theme.palette.mode]);

  const showError = !systemStatus.redisConnected || connectionState === 'error';

  return (
    <Box sx={{ p: 3, height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4" component="h1">
          Calls
        </Typography>
        <ConnectionStatus state={connectionState} />
      </Box>

      {/* Error Banner */}
      {showError && (
        <ErrorBanner
          redisConnected={systemStatus.redisConnected}
          connectionState={connectionState}
          errorMessage={systemStatus.errorMessage}
        />
      )}

      {/* Content */}
      {!showError && (
        <>
          {/* Summary Cards */}
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 3 }}>
            <SummaryCard
              title="In Queue"
              value={queueStats.queuedCount}
              icon={<QueueIcon sx={{ fontSize: 32 }} />}
              color="hsl(210, 98%, 42%)"
              bgColor="hsl(210, 100%, 95%)"
            />
            <SummaryCard
              title="Active Calls"
              value={calls.length}
              icon={<PhoneInTalkIcon sx={{ fontSize: 32 }} />}
              color="hsl(120, 59%, 30%)"
              bgColor="hsl(120, 80%, 95%)"
            />
            <SummaryCard
              title="Avg Wait"
              value={formatDuration(queueStats.avgWaitSeconds)}
              icon={<AccessTimeIcon sx={{ fontSize: 32 }} />}
              color="hsl(45, 90%, 40%)"
              bgColor="hsl(45, 100%, 95%)"
            />
            <SummaryCard
              title="Longest Wait"
              value={formatDuration(queueStats.longestWaitSeconds)}
              icon={<WarningIcon sx={{ fontSize: 32 }} />}
              color={queueStats.longestWaitSeconds > 120 ? 'hsl(0, 90%, 40%)' : 'hsl(220, 20%, 40%)'}
              bgColor={queueStats.longestWaitSeconds > 120 ? 'hsl(0, 100%, 95%)' : 'hsl(220, 20%, 95%)'}
            />
          </Box>

          {/* Grids */}
          <Box sx={{ display: 'flex', gap: 3, flex: 1, minHeight: 0 }}>
            {/* Queued Calls */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Queued Calls ({queuedCalls.length})
              </Typography>
              <Box sx={{ flex: 1 }}>
                <AgGridReact
                  ref={queuedGridRef}
                  rowData={queuedRowData}
                  columnDefs={queuedColumnDefs}
                  defaultColDef={defaultColDef}
                  theme={gridTheme}
                  context={gridContext}
                  getRowId={(params) => params.data.id}
                  suppressCellFocus={true}
                />
              </Box>
            </Box>

            {/* Active Calls */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Active Calls ({calls.length})
              </Typography>
              <Box sx={{ flex: 1 }}>
                <AgGridReact
                  ref={activeGridRef}
                  rowData={activeRowData}
                  columnDefs={activeColumnDefs}
                  defaultColDef={defaultColDef}
                  theme={gridTheme}
                  context={gridContext}
                  getRowId={(params) => params.data.id}
                  suppressCellFocus={true}
                />
              </Box>
            </Box>
          </Box>
        </>
      )}
    </Box>
  );
}
