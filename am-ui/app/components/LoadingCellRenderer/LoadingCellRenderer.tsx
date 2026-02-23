'use client';

import * as React from 'react';
import type { ICellRendererParams } from 'ag-grid-community';
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';

/** Skeleton placeholder shown in cells while row data is loading. */
export function LoadingSkeleton() {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', height: '100%', width: '100%' }}>
      <Skeleton variant="text" width="70%" height={24} animation="wave" />
    </Box>
  );
}

/**
 * Default cell renderer: shows a skeleton while row data is loading,
 * otherwise displays the formatted or raw value.
 * Set this on defaultColDef.cellRenderer.
 */
export function DefaultCellRenderer(params: ICellRendererParams) {
  if (!params.data) {
    return <LoadingSkeleton />;
  }
  return params.valueFormatted ?? params.value ?? '';
}
