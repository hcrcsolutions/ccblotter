'use client';

import * as React from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueGetterParams } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import type { QueuedCall } from '../../types';
import '../../lib/agGridSetup';
import { PRIORITY_COLORS } from '../../lib/statusColors';
import { formatDuration, calculateDuration } from '../../lib/formatters';
import { createGridDatasource, refreshVisibleRows } from '../../lib/gridDatasource';
import { DefaultCellRenderer, LoadingSkeleton } from '../LoadingCellRenderer/LoadingCellRenderer';

export interface QueuedCallGridProps {
  onMetadata?: (metadata: Record<string, unknown>) => void;
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

export function QueuedCallGrid({ onMetadata }: QueuedCallGridProps) {
  const theme = useTheme();
  const gridRef = React.useRef<AgGridReact>(null);
  const onMetadataRef = React.useRef(onMetadata);
  onMetadataRef.current = onMetadata;

  const datasource = React.useMemo(
    () => createGridDatasource<QueuedCall>({
      endpoint: '/queue/query',
      onMetadata: (meta) => onMetadataRef.current?.(meta),
    }),
    []
  );

  const columnDefs = React.useMemo<ColDef[]>(() => [
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

  const defaultColDef = React.useMemo<ColDef>(() => ({
    resizable: true,
    cellRenderer: DefaultCellRenderer,
  }), []);

  // Refresh visible rows from server every 2 seconds
  React.useEffect(() => {
    const interval = setInterval(() => {
      if (gridRef.current?.api) {
        refreshVisibleRows(gridRef.current.api, '/queue/query', 100, (meta) => onMetadataRef.current?.(meta), 'queuedCount');
      }
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  // Refresh computed cells every second for live wait times
  React.useEffect(() => {
    const interval = setInterval(() => {
      gridRef.current?.api?.refreshCells({ force: true });
    }, 1000);
    return () => clearInterval(interval);
  }, []);

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

  return (
    <Box sx={{ height: '100%', width: '100%' }}>
      <AgGridReact
        ref={gridRef}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        theme={gridTheme}
        context={gridContext}
        rowModelType="infinite"
        datasource={datasource}
        cacheBlockSize={100}
        maxBlocksInCache={100}
        rowBuffer={20}
        animateRows={false}
        getRowId={(params) => params.data.id}
        suppressCellFocus={true}
      />
    </Box>
  );
}
