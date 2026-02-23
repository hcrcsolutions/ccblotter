'use client';

import * as React from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueFormatterParams, ValueGetterParams } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import type { AgentRow, AgentState } from '../../types';
import '../../lib/agGridSetup';
import { AGENT_STATUS_COLORS } from '../../lib/statusColors';
import { formatDuration, formatTime, calculateDuration } from '../../lib/formatters';
import { createGridDatasource } from '../../lib/gridDatasource';
import { DefaultCellRenderer, LoadingSkeleton } from '../LoadingCellRenderer/LoadingCellRenderer';

// Status cell renderer with colored chip
function StatusCellRenderer(params: ICellRendererParams<AgentRow>) {
  if (!params.data) return <LoadingSkeleton />;
  const status = params.value as AgentState;
  if (!status) return null;
  const config = AGENT_STATUS_COLORS[status];
  if (!config) return null;
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
          '& .MuiChip-label': {
            px: 1.5,
          },
        }}
      />
    </Box>
  );
}

// Idle time cell renderer with warning colors for long idle times
function IdleTimeCellRenderer(params: ICellRendererParams<AgentRow>) {
  if (!params.data) return <LoadingSkeleton />;
  const idleSeconds = params.value as number | null;
  const state = params.data?.state;
  const isDark = params.context?.isDarkMode;

  // Only show idle time for ONLINE agents
  if (state !== 'ONLINE' || idleSeconds == null) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>-</Typography>
      </Box>
    );
  }

  // Color coding: >10 min = warning, >20 min = alert
  let color = isDark ? 'hsl(120, 70%, 65%)' : 'hsl(120, 59%, 30%)'; // Green - healthy
  if (idleSeconds > 1200) { // >20 min
    color = isDark ? 'hsl(0, 90%, 70%)' : 'hsl(0, 90%, 40%)'; // Red - alert
  } else if (idleSeconds > 600) { // >10 min
    color = isDark ? 'hsl(45, 100%, 65%)' : 'hsl(45, 90%, 35%)'; // Orange - warning
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
      <Typography variant="body2" sx={{ color, fontWeight: idleSeconds > 600 ? 600 : 400 }}>
        {formatDuration(idleSeconds)}
      </Typography>
    </Box>
  );
}

export function AgentGrid() {
  const theme = useTheme();
  const gridRef = React.useRef<AgGridReact<AgentRow>>(null);

  // Create datasource for infinite row model
  const datasource = React.useMemo(
    () => createGridDatasource<AgentRow>({ endpoint: '/agents/query' }),
    []
  );

  // Column definitions with valueGetters for computed duration fields
  const columnDefs = React.useMemo<ColDef<AgentRow>[]>(() => [
    {
      field: 'id',
      headerName: 'Agent ID',
      filter: 'agTextColumnFilter',
      sortable: true,
      width: 110,
      pinned: 'left',
    },
    {
      field: 'name',
      headerName: 'Agent Name',
      filter: 'agTextColumnFilter',
      sortable: true,
      width: 160,
      pinned: 'left',
    },
    {
      field: 'state',
      headerName: 'Status',
      filter: 'agTextColumnFilter',
      sortable: true,
      width: 120,
      cellRenderer: StatusCellRenderer,
      filterValueGetter: (params: ValueGetterParams<AgentRow>) => AGENT_STATUS_COLORS[params.data?.state as AgentState]?.label || '',
    },
    {
      colId: 'timeInStatus',
      headerName: 'Time in Status',
      filter: 'agNumberColumnFilter',
      sortable: true,
      width: 130,
      valueGetter: (params: ValueGetterParams<AgentRow>) =>
        params.data?.stateChangedAt ? calculateDuration(params.data.stateChangedAt) : null,
      valueFormatter: (params: ValueFormatterParams<AgentRow>) => formatDuration(params.value),
    },
    {
      headerName: 'Current Call',
      marryChildren: true,
      children: [
        {
          field: 'currentCaller',
          headerName: 'Caller',
          filter: 'agTextColumnFilter',
          sortable: true,
          width: 140,
          valueFormatter: (params: ValueFormatterParams<AgentRow>) => params.value || '-',
        },
        {
          colId: 'callDuration',
          headerName: 'Duration',
          filter: 'agNumberColumnFilter',
          sortable: true,
          width: 100,
          valueGetter: (params: ValueGetterParams<AgentRow>) =>
            params.data?.callStartTime ? calculateDuration(params.data.callStartTime) : null,
          valueFormatter: (params: ValueFormatterParams<AgentRow>) => formatDuration(params.value),
        },
      ],
    },
    {
      headerName: 'Previous Call',
      marryChildren: true,
      children: [
        {
          colId: 'lastCaller',
          headerName: 'Caller',
          filter: 'agTextColumnFilter',
          sortable: true,
          width: 140,
          valueGetter: (params: ValueGetterParams<AgentRow>) => params.data?.lastCallOriginator || null,
          valueFormatter: (params: ValueFormatterParams<AgentRow>) => params.value || '-',
        },
        {
          colId: 'lastCallTime',
          headerName: 'Ended At',
          filter: 'agTextColumnFilter',
          sortable: true,
          width: 110,
          valueGetter: (params: ValueGetterParams<AgentRow>) => params.data?.lastCallEndTime || null,
          valueFormatter: (params: ValueFormatterParams<AgentRow>) => formatTime(params.value),
        },
        {
          colId: 'lastCallDuration',
          headerName: 'Duration',
          filter: 'agNumberColumnFilter',
          sortable: true,
          width: 100,
          valueGetter: (params: ValueGetterParams<AgentRow>) => params.data?.lastCallDurationSeconds ?? null,
          valueFormatter: (params: ValueFormatterParams<AgentRow>) => formatDuration(params.value),
        },
      ],
    },
    {
      colId: 'idleTime',
      headerName: 'Idle Time',
      filter: 'agNumberColumnFilter',
      sortable: true,
      width: 110,
      valueGetter: (params: ValueGetterParams<AgentRow>) => {
        if (params.data?.state === 'ONLINE' && params.data?.lastCallEndTime) {
          return calculateDuration(params.data.lastCallEndTime);
        }
        return null;
      },
      cellRenderer: IdleTimeCellRenderer,
      headerTooltip: 'Time since last call ended (ONLINE agents only). Warning at 10min, alert at 20min.',
    },
  ], []);

  // Default column settings
  const defaultColDef = React.useMemo<ColDef>(() => ({
    resizable: true,
    floatingFilter: true,
    cellRenderer: DefaultCellRenderer,
  }), []);

  // Refresh data from server every 5 seconds
  React.useEffect(() => {
    const interval = setInterval(() => {
      if (gridRef.current?.api) {
        gridRef.current.api.refreshInfiniteCache();
      }
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  // Refresh computed cells every second for live durations
  React.useEffect(() => {
    const interval = setInterval(() => {
      if (gridRef.current?.api) {
        gridRef.current.api.refreshCells({ force: true });
      }
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  // AG Grid theme based on MUI theme
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

  // Pass dark mode to cell renderers via context
  const gridContext = React.useMemo(() => ({
    isDarkMode: theme.palette.mode === 'dark',
  }), [theme.palette.mode]);

  return (
    <Box sx={{ height: '100%', width: '100%' }}>
      <AgGridReact<AgentRow>
        ref={gridRef}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        theme={gridTheme}
        context={gridContext}
        rowModelType="infinite"
        datasource={datasource}
        cacheBlockSize={100}
        maxBlocksInCache={500}
        rowBuffer={20}
        animateRows={false}
        getRowId={(params) => params.data.id}
        suppressCellFocus={true}
      />
    </Box>
  );
}
