'use client';

import * as React from 'react';
import { Stack } from '@mui/material';
import { DashboardLayout, ThemeSwitcher } from '@toolpad/core/DashboardLayout';
import { useTheme } from '@mui/material/styles';
import { usePathname } from 'next/navigation';
import { WebSocketProvider, useWebSocketContext } from '../context/WebSocketContext';
import { ConnectionStatus } from '../components/ConnectionStatus/ConnectionStatus';

// Wrapper to sync theme with HTML attribute
function ThemeSwitcherWithSync() {
  const theme = useTheme();

  React.useEffect(() => {
    document.documentElement.setAttribute('data-toolpad-color-scheme', theme.palette.mode);
  }, [theme.palette.mode]);

  return <ThemeSwitcher />;
}

function ToolbarActions() {
  const { connectionState } = useWebSocketContext();
  const pathname = usePathname();
  const showConnectionStatus = pathname !== '/settings';

  return (
    <Stack direction="row" alignItems="center" spacing={2}>
      {showConnectionStatus && <ConnectionStatus state={connectionState} />}
      <ThemeSwitcherWithSync />
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
      <DashboardLayout
        defaultSidebarCollapsed
        slots={{
          toolbarActions: ToolbarActions,
        }}
      >
        {children}
      </DashboardLayout>
    </WebSocketProvider>
  );
}
