'use client';

import * as React from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueFormatterParams, ValueGetterParams } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import type { Agent, Call, AgentState } from '../../types';
import '../../lib/agGridSetup';
import { AGENT_STATUS_COLORS } from '../../lib/statusColors';
import { formatDuration, formatTime, calculateDuration } from '../../lib/formatters';

// Status cell renderer with colored chip
function StatusCellRenderer(params: ICellRendererParams<AgentRowData>) {
  const status = params.value as AgentState;
  const config = AGENT_STATUS_COLORS[status];
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
function IdleTimeCellRenderer(params: ICellRendererParams<AgentRowData>) {
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

interface AgentRowData {
  id: string;
  name: string;
  state: AgentState;
  stateChangedAt: string;
  timeInStatus: number;
  // Current call info (if ON_CALL)
  currentCaller: string | null;
  callDuration: number | null;
  callStartTime: string | null;
  // Last call info (historical)
  lastCaller: string | null;
  lastCallTime: string | null;
  lastCallDuration: number | null;
  // Calculated fields
  idleTime: number | null; // Time since last call ended (for ONLINE agents)
}

interface AgentGridProps {
  agents: Agent[];
  calls: Call[];
}

export function AgentGrid({ agents, calls }: AgentGridProps) {
  const theme = useTheme();
  const gridRef = React.useRef<AgGridReact<AgentRowData>>(null);
  const [rowData, setRowData] = React.useState<AgentRowData[]>([]);

  // Create a stable key for calls array to optimize memoization
  // Only recompute map when actual call data changes, not just array reference
  const callsKey = React.useMemo(() => {
    return calls.map(c => `${c.agentId}:${c.id}`).join(',');
  }, [calls]);

  // Create a map of agentId -> call for quick lookup
  const callsByAgent = React.useMemo(() => {
    const map = new Map<string, Call>();
    calls.forEach(call => map.set(call.agentId, call));
    return map;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [callsKey]);

  // Column definitions
  const columnDefs = React.useMemo<ColDef<AgentRowData>[]>(() => [
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
      filterValueGetter: (params: ValueGetterParams<AgentRowData>) => AGENT_STATUS_COLORS[params.data?.state as AgentState]?.label || '',
    },
    {
      field: 'timeInStatus',
      headerName: 'Time in Status',
      filter: 'agNumberColumnFilter',
      sortable: true,
      width: 130,
      valueFormatter: (params: ValueFormatterParams<AgentRowData>) => formatDuration(params.value),
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
          valueFormatter: (params: ValueFormatterParams<AgentRowData>) => params.value || '-',
        },
        {
          field: 'callDuration',
          headerName: 'Duration',
          filter: 'agNumberColumnFilter',
          sortable: true,
          width: 100,
          valueFormatter: (params: ValueFormatterParams<AgentRowData>) => formatDuration(params.value),
        },
      ],
    },
    {
      headerName: 'Previous Call',
      marryChildren: true,
      children: [
        {
          field: 'lastCaller',
          headerName: 'Caller',
          filter: 'agTextColumnFilter',
          sortable: true,
          width: 140,
          valueFormatter: (params: ValueFormatterParams<AgentRowData>) => params.value || '-',
        },
        {
          field: 'lastCallTime',
          headerName: 'Ended At',
          filter: 'agTextColumnFilter',
          sortable: true,
          width: 110,
          valueFormatter: (params: ValueFormatterParams<AgentRowData>) => formatTime(params.value),
        },
        {
          field: 'lastCallDuration',
          headerName: 'Duration',
          filter: 'agNumberColumnFilter',
          sortable: true,
          width: 100,
          valueFormatter: (params: ValueFormatterParams<AgentRowData>) => formatDuration(params.value),
        },
      ],
    },
    {
      field: 'idleTime',
      headerName: 'Idle Time',
      filter: 'agNumberColumnFilter',
      sortable: true,
      width: 110,
      cellRenderer: IdleTimeCellRenderer,
      headerTooltip: 'Time since last call ended (ONLINE agents only). Warning at 10min, alert at 20min.',
    },
  ], []);

  // Default column settings
  const defaultColDef = React.useMemo<ColDef>(() => ({
    resizable: true,
    floatingFilter: true,
  }), []);

  // Update row data with calculated durations every second
  React.useEffect(() => {
    const updateData = () => {
      const rows: AgentRowData[] = agents.map(agent => {
        const call = callsByAgent.get(agent.id);

        // Calculate idle time for ONLINE agents
        let idleTime: number | null = null;
        if (agent.state === 'ONLINE' && agent.lastCallEndTime) {
          idleTime = calculateDuration(agent.lastCallEndTime);
        }

        return {
          id: agent.id,
          name: agent.name,
          state: agent.state,
          stateChangedAt: agent.stateChangedAt,
          timeInStatus: calculateDuration(agent.stateChangedAt) || 0,
          // Current call
          currentCaller: call?.originator || null,
          callDuration: call ? calculateDuration(call.startTime) : null,
          callStartTime: call?.startTime || null,
          // Last call
          lastCaller: agent.lastCallOriginator || null,
          lastCallTime: agent.lastCallEndTime || null,
          lastCallDuration: agent.lastCallDurationSeconds || null,
          // Idle time
          idleTime,
        };
      });

      setRowData(rows);
    };

    // Initial update
    updateData();

    // Update every second for live durations
    const interval = setInterval(updateData, 1000);

    return () => clearInterval(interval);
  }, [agents, callsByAgent]);

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

  // Force refresh cells when grid context changes to update pill colors
  // Using a ref to track if this is the initial render
  const isInitialRenderRef = React.useRef(true);
  React.useEffect(() => {
    // Skip the initial render to avoid unnecessary refresh
    if (isInitialRenderRef.current) {
      isInitialRenderRef.current = false;
      return;
    }
    // Use requestAnimationFrame to ensure grid context is updated before refresh
    requestAnimationFrame(() => {
      if (gridRef.current?.api) {
        gridRef.current.api.refreshCells({ force: true });
      }
    });
  }, [gridContext]);

  return (
    <Box sx={{ height: '100%', width: '100%' }}>
      <AgGridReact<AgentRowData>
        ref={gridRef}
        rowData={rowData}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        theme={gridTheme}
        context={gridContext}
        rowBuffer={20}
        animateRows={false}
        getRowId={(params) => params.data.id}
        suppressCellFocus={true}
      />
    </Box>
  );
}
