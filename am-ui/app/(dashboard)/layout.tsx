'use client';

import * as React from 'react';
import { Stack } from '@mui/material';
import { DashboardLayout, ThemeSwitcher } from '@toolpad/core/DashboardLayout';
import { useTheme } from '@mui/material/styles';

// Wrapper to sync theme with HTML attribute
function ThemeSwitcherWithSync() {
  const theme = useTheme();

  React.useEffect(() => {
    document.documentElement.setAttribute('data-toolpad-color-scheme', theme.palette.mode);
  }, [theme.palette.mode]);

  return <ThemeSwitcher />;
}

function ToolbarActions() {
  return (
    <Stack direction="row" alignItems="center" spacing={1}>
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
    <DashboardLayout
      defaultSidebarCollapsed
      slots={{
        toolbarActions: ToolbarActions,
      }}
    >
      {children}
    </DashboardLayout>
  );
}
