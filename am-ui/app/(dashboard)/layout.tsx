'use client';

import * as React from 'react';
import { Stack } from '@mui/material';
import { usePathname } from 'next/navigation';
import { DashboardLayout } from '../components/DashboardLayout/DashboardLayout';
import { WebSocketProvider, useWebSocketContext } from '../context/WebSocketContext';
import { ConnectionStatus } from '../components/ConnectionStatus/ConnectionStatus';

function ToolbarActions() {
  const { connectionState } = useWebSocketContext();
  const pathname = usePathname();
  const showConnectionStatus = pathname !== '/settings';

  return (
    <Stack direction="row" alignItems="center" spacing={2}>
      {showConnectionStatus && <ConnectionStatus state={connectionState} />}
    </Stack>
  );
}

export default function DashboardPagesLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <WebSocketProvider>
      <DashboardLayout toolbarActions={<ToolbarActions />}>
        {children}
      </DashboardLayout>
    </WebSocketProvider>
  );
}
