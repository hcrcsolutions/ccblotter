'use client';

import * as React from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef } from 'ag-grid-community';
import { themeQuartz } from 'ag-grid-community';
import Box from '@mui/material/Box';
import { useTheme } from '@mui/material/styles';
import type { Call } from '../../types';
import '../../lib/agGridSetup';
import { formatDuration, calculateDuration } from '../../lib/formatters';

interface CallWithDuration extends Call {
  duration: number;
}

interface CallBlotterProps {
  calls: Call[];
}

export function CallBlotter({ calls }: CallBlotterProps) {
  const theme = useTheme();
  const gridRef = React.useRef<AgGridReact<CallWithDuration>>(null);
  const [rowData, setRowData] = React.useState<CallWithDuration[]>([]);

  // Column definitions
  const columnDefs = React.useMemo<ColDef<CallWithDuration>[]>(() => [
    {
      field: 'originator',
      headerName: 'Caller',
      filter: 'agTextColumnFilter',
      sortable: true,
      flex: 1,
      minWidth: 150,
    },
    {
      field: 'agentName',
      headerName: 'Agent',
      filter: 'agTextColumnFilter',
      sortable: true,
      flex: 1,
      minWidth: 180,
    },
    {
      field: 'startTime',
      headerName: 'Start Time',
      filter: 'agDateColumnFilter',
      sortable: true,
      flex: 1,
      minWidth: 180,
      valueFormatter: (params) => {
        if (!params.value) return '';
        return new Date(params.value).toLocaleTimeString('en-US', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        });
      },
      comparator: (valueA, valueB) => {
        return new Date(valueA).getTime() - new Date(valueB).getTime();
      },
    },
    {
      field: 'duration',
      headerName: 'Duration',
      filter: 'agNumberColumnFilter',
      sortable: true,
      width: 120,
      valueFormatter: (params) => formatDuration(params.value || 0, 'short'),
    },
  ], []);

  // Default column settings
  const defaultColDef = React.useMemo<ColDef>(() => ({
    resizable: true,
    floatingFilter: true,
  }), []);

  // Update durations every second
  React.useEffect(() => {
    const updateDurations = () => {
      const updatedRows = calls.map((call) => ({
        ...call,
        duration: calculateDuration(call.startTime) ?? 0,
      }));
      setRowData(updatedRows);
    };

    // Initial update
    updateDurations();

    // Update every second
    const interval = setInterval(updateDurations, 1000);

    return () => clearInterval(interval);
  }, [calls]);

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
      headerHeight: 48,
    });
  }, [theme]);

  return (
    <Box sx={{ height: '100%', width: '100%' }}>
      <AgGridReact<CallWithDuration>
        ref={gridRef}
        rowData={rowData}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        theme={gridTheme}
        rowBuffer={20}
        animateRows={false}
        getRowId={(params) => params.data.id}
        suppressCellFocus={true}
      />
    </Box>
  );
}
