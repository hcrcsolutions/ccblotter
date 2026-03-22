'use client';

import * as React from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams, ValueFormatterParams } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { useTheme } from '@mui/material/styles';
import type { ScenarioSummary } from '../../types';
import '../../lib/agGridSetup';
import { createGridDatasource } from '../../lib/gridDatasource';
import { DefaultCellRenderer, LoadingSkeleton } from '../LoadingCellRenderer/LoadingCellRenderer';

export interface ScenarioGridProps {
  onEdit: (scenarioId: string) => void;
  onDelete: (scenario: ScenarioSummary) => void;
  onMetadata?: (metadata: Record<string, unknown>) => void;
}

export interface ScenarioGridRef {
  refresh: () => void;
}

function NameCellRenderer(params: ICellRendererParams) {
  if (!params.data) {
    return <LoadingSkeleton />;
  }
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', height: '100%' }}>
      <Typography variant="body2" sx={{ fontWeight: 500, lineHeight: 1.3 }}>
        {params.data.name}
      </Typography>
      {params.data.description && (
        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
          {params.data.description}
        </Typography>
      )}
    </Box>
  );
}

function StatusCellRenderer(params: ICellRendererParams) {
  if (!params.data) {
    return <LoadingSkeleton />;
  }
  const status = params.value as string;
  if (!status) {
    return null;
  }
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
      <Chip
        label={status}
        size="small"
        color={status === 'READY' ? 'success' : 'default'}
        variant="outlined"
      />
    </Box>
  );
}

function LastRunCellRenderer(params: ICellRendererParams) {
  if (!params.data) {
    return <LoadingSkeleton />;
  }
  const result = params.data.lastRunResult as string | null;
  if (!result) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
        <Typography variant="caption" color="text.secondary">Never</Typography>
      </Box>
    );
  }
  const colorMap: Record<string, 'success' | 'error' | 'warning'> = {
    PASS: 'success',
    FAIL: 'error',
    ERROR: 'warning',
  };
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
      <Chip
        label={result}
        size="small"
        color={colorMap[result] || 'default'}
        variant="outlined"
      />
    </Box>
  );
}

export const ScenarioGrid = React.forwardRef<ScenarioGridRef, ScenarioGridProps>(
  function ScenarioGrid({ onEdit, onDelete, onMetadata }, ref) {
    const theme = useTheme();
    const gridRef = React.useRef<AgGridReact>(null);
    const onMetadataRef = React.useRef(onMetadata);
    onMetadataRef.current = onMetadata;

    const callbacksRef = React.useRef({ onEdit, onDelete });
    callbacksRef.current = { onEdit, onDelete };

    React.useImperativeHandle(ref, () => ({
      refresh() {
        gridRef.current?.api?.refreshInfiniteCache();
      },
    }));

    const datasource = React.useMemo(
      () => createGridDatasource<ScenarioSummary>({
        endpoint: '/v1/test-scenarios/query',
        onMetadata: (meta) => onMetadataRef.current?.(meta),
      }),
      []
    );

    const columnDefs = React.useMemo<ColDef[]>(() => [
      {
        field: 'name',
        headerName: 'Name',
        flex: 2,
        minWidth: 200,
        filter: 'agTextColumnFilter',
        sortable: true,
        cellRenderer: NameCellRenderer,
      },
      {
        field: 'status',
        headerName: 'Status',
        width: 120,
        filter: 'agTextColumnFilter',
        sortable: true,
        cellRenderer: StatusCellRenderer,
      },
      {
        field: 'version',
        headerName: 'Version',
        width: 100,
        filter: 'agNumberColumnFilter',
        sortable: true,
      },
      {
        field: 'lastRunResult',
        headerName: 'Last Run',
        width: 120,
        sortable: true,
        cellRenderer: LastRunCellRenderer,
      },
      {
        field: 'updatedAt',
        headerName: 'Updated',
        width: 180,
        sortable: true,
        valueFormatter: (params: ValueFormatterParams) => {
          if (!params.value) {
            return '';
          }
          return new Date(params.value).toLocaleString();
        },
      },
      {
        colId: 'actions',
        headerName: '',
        width: 100,
        sortable: false,
        suppressHeaderMenuButton: true,
        cellRenderer: (params: ICellRendererParams) => {
          if (!params.data) {
            return null;
          }
          return (
            <Box sx={{ display: 'flex', alignItems: 'center', height: '100%', gap: 0.5 }}>
              <IconButton
                size="small"
                onClick={() => callbacksRef.current.onEdit(params.data.id)}
                title="Edit scenario"
              >
                <EditIcon fontSize="small" />
              </IconButton>
              <IconButton
                size="small"
                onClick={() => callbacksRef.current.onDelete(params.data)}
                color="error"
                title="Delete scenario"
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Box>
          );
        },
      },
    ], []);

    const defaultColDef = React.useMemo<ColDef>(() => ({
      resizable: true,
      cellRenderer: DefaultCellRenderer,
    }), []);

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

    return (
      <Box sx={{ height: '100%', width: '100%' }}>
        <AgGridReact
          ref={gridRef}
          columnDefs={columnDefs}
          defaultColDef={defaultColDef}
          theme={gridTheme}
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
);
