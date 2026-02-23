'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueFormatterParams, ValueGetterParams } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Chip from '@mui/material/Chip';
import QueueIcon from '@mui/icons-material/Queue';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import WarningIcon from '@mui/icons-material/Warning';
import { useWebSocketContext } from '../../context/WebSocketContext';
import { ErrorBanner } from '../../components/ErrorBanner/ErrorBanner';
import { SummaryCard } from '../../components/SummaryCard/SummaryCard';
import type { Call, CallState, QueuedCall, QueueStats } from '../../types';
import '../../lib/agGridSetup';
import { CALL_STATE_COLORS, PRIORITY_COLORS } from '../../lib/statusColors';
import { formatDuration, calculateDuration } from '../../lib/formatters';
import { createGridDatasource, refreshVisibleRows } from '../../lib/gridDatasource';
import { DefaultCellRenderer, LoadingSkeleton } from '../../components/LoadingCellRenderer/LoadingCellRenderer';

// Call state cell renderer
function CallStateCellRenderer(params: ICellRendererParams) {
  if (!params.data) return <LoadingSkeleton />;
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
  if (!params.data) return <LoadingSkeleton />;
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
  if (!params.data) return <LoadingSkeleton />;
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
  const { systemStatus, connectionState } = useWebSocketContext();

  const queuedGridRef = React.useRef<AgGridReact>(null);
  const activeGridRef = React.useRef<AgGridReact>(null);

  // Local state from grid metadata
  const [queueStats, setQueueStats] = React.useState<QueueStats>({
    queuedCount: 0,
    avgWaitSeconds: 0,
    longestWaitSeconds: 0,
  });
  const [activeCallCount, setActiveCallCount] = React.useState(0);

  // Datasources for infinite row model
  const queueDatasource = React.useMemo(
    () => createGridDatasource<QueuedCall>({
      endpoint: '/queue/query',
      onMetadata: (meta) => {
        setQueueStats({
          queuedCount: (meta.queuedCount as number) || 0,
          avgWaitSeconds: (meta.avgWaitSeconds as number) || 0,
          longestWaitSeconds: (meta.longestWaitSeconds as number) || 0,
        });
      },
    }),
    []
  );

  const callsDatasource = React.useMemo(
    () => createGridDatasource<Call>({
      endpoint: '/calls/query',
      onMetadata: (meta) => {
        setActiveCallCount((meta.activeCallCount as number) || 0);
      },
    }),
    []
  );

  // Queued calls column definitions
  const queuedColumnDefs = React.useMemo<ColDef[]>(() => [
    {
      colId: 'position',
      headerName: '#',
      width: 60,
      sortable: false,
      valueGetter: (params: ValueGetterParams) =>
        params.node?.rowIndex != null ? params.node.rowIndex + 1 : null,
    },
    { field: 'originator', headerName: 'Caller', width: 140, filter: 'agTextColumnFilter', sortable: true },
    { field: 'skill', headerName: 'Skill', width: 100, filter: 'agTextColumnFilter', sortable: true },
    { field: 'priority', headerName: 'Priority', width: 100, cellRenderer: PriorityCellRenderer, sortable: true },
    {
      colId: 'waitTime',
      headerName: 'Wait Time',
      width: 110,
      sortable: true,
      valueGetter: (params: ValueGetterParams) =>
        params.data?.queuedAt ? calculateDuration(params.data.queuedAt) : null,
      cellRenderer: WaitTimeCellRenderer,
    },
  ], []);

  // Active calls column definitions
  const activeColumnDefs = React.useMemo<ColDef[]>(() => [
    { field: 'originator', headerName: 'Caller', width: 140, filter: 'agTextColumnFilter', sortable: true },
    { field: 'agentName', headerName: 'Agent', width: 160, filter: 'agTextColumnFilter', sortable: true },
    { field: 'state', headerName: 'State', width: 100, cellRenderer: CallStateCellRenderer, sortable: true },
    {
      colId: 'duration',
      headerName: 'Duration',
      width: 100,
      sortable: true,
      valueGetter: (params: ValueGetterParams) =>
        params.data?.startTime ? calculateDuration(params.data.startTime) : null,
      valueFormatter: (params: ValueFormatterParams) => formatDuration(params.value),
    },
  ], []);

  const defaultColDef = React.useMemo<ColDef>(() => ({
    resizable: true,
    cellRenderer: DefaultCellRenderer,
  }), []);

  // Refresh visible rows from server every 5 seconds.
  // Updates nodes in place via setData() — no cache purge, no loading flicker.
  React.useEffect(() => {
    const interval = setInterval(() => {
      if (queuedGridRef.current?.api) {
        refreshVisibleRows(queuedGridRef.current.api, '/queue/query', 100, (meta) => {
          setQueueStats({
            queuedCount: (meta.queuedCount as number) || 0,
            avgWaitSeconds: (meta.avgWaitSeconds as number) || 0,
            longestWaitSeconds: (meta.longestWaitSeconds as number) || 0,
          });
        }, 'queuedCount');
      }
      if (activeGridRef.current?.api) {
        refreshVisibleRows(activeGridRef.current.api, '/calls/query', 100, (meta) => {
          setActiveCallCount((meta.activeCallCount as number) || 0);
        }, 'activeCallCount');
      }
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  // Refresh computed cells every second for live durations
  React.useEffect(() => {
    const interval = setInterval(() => {
      queuedGridRef.current?.api?.refreshCells({ force: true });
      activeGridRef.current?.api?.refreshCells({ force: true });
    }, 1000);
    return () => clearInterval(interval);
  }, []);

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

  const showError = !systemStatus.redisConnected || connectionState === 'error';

  return (
    <Box sx={{ p: 3, height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
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
              width={250}
            />
            <SummaryCard
              title="Active Calls"
              value={activeCallCount}
              icon={<PhoneInTalkIcon sx={{ fontSize: 32 }} />}
              color="hsl(120, 59%, 30%)"
              bgColor="hsl(120, 80%, 95%)"
              width={250}
            />
            <SummaryCard
              title="Avg Wait"
              value={formatDuration(queueStats.avgWaitSeconds)}
              icon={<AccessTimeIcon sx={{ fontSize: 32 }} />}
              color="hsl(45, 90%, 40%)"
              bgColor="hsl(45, 100%, 95%)"
              width={250}
            />
            <SummaryCard
              title="Longest Wait"
              value={formatDuration(queueStats.longestWaitSeconds)}
              icon={<WarningIcon sx={{ fontSize: 32 }} />}
              color={queueStats.longestWaitSeconds > 120 ? 'hsl(0, 90%, 40%)' : 'hsl(220, 20%, 40%)'}
              bgColor={queueStats.longestWaitSeconds > 120 ? 'hsl(0, 100%, 95%)' : 'hsl(220, 20%, 95%)'}
              width={250}
            />
          </Box>

          {/* Grids */}
          <Box sx={{ display: 'flex', gap: 3, flex: 1, minHeight: 0 }}>
            {/* Queued Calls */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Queued Calls ({queueStats.queuedCount})
              </Typography>
              <Box sx={{ flex: 1 }}>
                <AgGridReact
                  ref={queuedGridRef}
                  columnDefs={queuedColumnDefs}
                  defaultColDef={defaultColDef}
                  theme={gridTheme}
                  context={gridContext}
                  rowModelType="infinite"
                  datasource={queueDatasource}
                  cacheBlockSize={100}
                  maxBlocksInCache={100}
                  rowBuffer={20}
                  animateRows={false}
                  getRowId={(params) => params.data.id}
                  suppressCellFocus={true}
                />
              </Box>
            </Box>

            {/* Active Calls */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Active Calls ({activeCallCount})
              </Typography>
              <Box sx={{ flex: 1 }}>
                <AgGridReact
                  ref={activeGridRef}
                  columnDefs={activeColumnDefs}
                  defaultColDef={defaultColDef}
                  theme={gridTheme}
                  context={gridContext}
                  rowModelType="infinite"
                  datasource={callsDatasource}
                  cacheBlockSize={100}
                  maxBlocksInCache={100}
                  rowBuffer={20}
                  animateRows={false}
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
