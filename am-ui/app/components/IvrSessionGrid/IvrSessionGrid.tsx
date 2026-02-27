'use client';

import * as React from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueFormatterParams, ValueGetterParams, RowClickedEvent } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import CheckIcon from '@mui/icons-material/Check';
import { useTheme } from '@mui/material/styles';
import type { IvrSessionRow, IvrState } from '../../types';
import '../../lib/agGridSetup';
import { IVR_STATE_COLORS } from '../../lib/statusColors';
import { formatDuration, calculateDuration, formatTime } from '../../lib/formatters';
import { createGridDatasource, refreshVisibleRows } from '../../lib/gridDatasource';
import { DefaultCellRenderer, LoadingSkeleton } from '../LoadingCellRenderer/LoadingCellRenderer';

export interface IvrSessionGridProps {
  onMetadata?: (metadata: Record<string, unknown>) => void;
  onSelectSession?: (sessionId: string) => void;
}

function IvrStateCellRenderer(params: ICellRendererParams) {
  if (!params.data) return <LoadingSkeleton />;
  const state = params.value as IvrState;
  if (!state) return null;
  const config = IVR_STATE_COLORS[state];
  if (!config) return state;
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

function AuthCellRenderer(params: ICellRendererParams) {
  if (!params.data) return <LoadingSkeleton />;
  if (!params.value) return null;
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
      <CheckIcon sx={{ fontSize: 18, color: 'success.main' }} />
    </Box>
  );
}

export function IvrSessionGrid({ onMetadata, onSelectSession }: IvrSessionGridProps) {
  const theme = useTheme();
  const gridRef = React.useRef<AgGridReact>(null);
  const onMetadataRef = React.useRef(onMetadata);
  onMetadataRef.current = onMetadata;

  const datasource = React.useMemo(
    () => createGridDatasource<IvrSessionRow>({
      endpoint: '/ivr-sessions/query',
      onMetadata: (meta) => onMetadataRef.current?.(meta),
    }),
    []
  );

  const columnDefs = React.useMemo<ColDef[]>(() => [
    { field: 'originator', headerName: 'Caller', width: 140, filter: 'agTextColumnFilter', sortable: true },
    { field: 'flowId', headerName: 'Flow', width: 150, filter: 'agTextColumnFilter', sortable: true },
    { field: 'state', headerName: 'State', width: 130, cellRenderer: IvrStateCellRenderer, sortable: true },
    { field: 'currentStep', headerName: 'Current Step', width: 160, filter: 'agTextColumnFilter', sortable: true },
    { field: 'stepCount', headerName: 'Steps', width: 80, sortable: true },
    {
      field: 'startTime',
      headerName: 'Started',
      width: 110,
      sortable: true,
      valueFormatter: (params: ValueFormatterParams) => formatTime(params.value),
    },
    {
      colId: 'duration',
      headerName: 'Duration',
      width: 100,
      sortable: true,
      valueGetter: (params: ValueGetterParams) => {
        if (!params.data?.startTime) return null;
        if (params.data.endTime) {
          return Math.floor((new Date(params.data.endTime).getTime() - new Date(params.data.startTime).getTime()) / 1000);
        }
        return calculateDuration(params.data.startTime);
      },
      valueFormatter: (params: ValueFormatterParams) => formatDuration(params.value),
    },
    { field: 'authenticated', headerName: 'Auth', width: 70, cellRenderer: AuthCellRenderer, sortable: true },
  ], []);

  const defaultColDef = React.useMemo<ColDef>(() => ({
    resizable: true,
    cellRenderer: DefaultCellRenderer,
  }), []);

  // Refresh visible rows from server every 2 seconds
  React.useEffect(() => {
    const interval = setInterval(() => {
      if (gridRef.current?.api) {
        refreshVisibleRows(gridRef.current.api, '/ivr-sessions/query', 100, (meta) => onMetadataRef.current?.(meta), 'sessionCount');
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

  const handleRowClicked = React.useCallback((event: RowClickedEvent) => {
    if (event.data && onSelectSession) {
      onSelectSession(event.data.id);
    }
  }, [onSelectSession]);

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
        onRowClicked={handleRowClicked}
        rowStyle={{ cursor: 'pointer' }}
      />
    </Box>
  );
}
