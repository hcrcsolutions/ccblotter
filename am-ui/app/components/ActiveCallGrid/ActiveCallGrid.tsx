'use client';

import * as React from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueFormatterParams, ValueGetterParams } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import { useTheme } from '@mui/material/styles';
import type { Call, CallState } from '../../types';
import '../../lib/agGridSetup';
import { CALL_STATE_COLORS } from '../../lib/statusColors';
import { formatDuration, calculateDuration } from '../../lib/formatters';
import { createGridDatasource, refreshVisibleRows } from '../../lib/gridDatasource';
import { DefaultCellRenderer, LoadingSkeleton } from '../LoadingCellRenderer/LoadingCellRenderer';

export interface ActiveCallGridProps {
  onMetadata?: (metadata: Record<string, unknown>) => void;
}

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

export function ActiveCallGrid({ onMetadata }: ActiveCallGridProps) {
  const theme = useTheme();
  const gridRef = React.useRef<AgGridReact>(null);
  const onMetadataRef = React.useRef(onMetadata);
  onMetadataRef.current = onMetadata;

  const datasource = React.useMemo(
    () => createGridDatasource<Call>({
      endpoint: '/calls/query',
      onMetadata: (meta) => onMetadataRef.current?.(meta),
    }),
    []
  );

  const columnDefs = React.useMemo<ColDef[]>(() => [
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

  // Refresh visible rows from server every 2 seconds
  React.useEffect(() => {
    const interval = setInterval(() => {
      if (gridRef.current?.api) {
        refreshVisibleRows(gridRef.current.api, '/calls/query', 100, (meta) => onMetadataRef.current?.(meta), 'activeCallCount');
      }
    }, 2000);
    return () => clearInterval(interval);
  }, []);

  // Refresh computed cells every second for live durations
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
