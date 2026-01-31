'use client';

import * as React from 'react';
import { Suspense } from 'react';
import dynamic from 'next/dynamic';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import { useWebSocketContext } from '../../context/WebSocketContext';

// Dynamic import to avoid SSR issues with dagre (CommonJS)
const InfrastructureFlow = dynamic(
  () => import('../../components/InfrastructureFlow/InfrastructureFlow').then(mod => mod.InfrastructureFlow),
  {
    ssr: false,
    loading: () => (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <CircularProgress />
      </Box>
    ),
  }
);

export default function InfrastructurePage() {
  const { infrastructure, infrastructureSummary } = useWebSocketContext();

  return (
    <Box
      sx={{
        p: 3,
        height: 'calc(100vh - 64px)', // Subtract AppBar height
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Suspense
        fallback={
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
            <CircularProgress />
          </Box>
        }
      >
        <InfrastructureFlow
          topology={infrastructure}
          summary={infrastructureSummary}
        />
      </Suspense>
    </Box>
  );
}
