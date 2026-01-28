'use client';

import * as React from 'react';
import { Suspense } from 'react';
import { NextAppProvider } from '@toolpad/core/nextjs';
import { AppRouterCacheProvider } from '@mui/material-nextjs/v15-appRouter';
import type { Navigation } from '@toolpad/core/AppProvider';
import { createTheme } from '@mui/material/styles';
import getMPTheme from '@/theme/getMPTheme';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PhoneIcon from '@mui/icons-material/Phone';

// Navigation configuration for sidebar
const NAVIGATION: Navigation = [
  {
    segment: '',
    title: 'Dashboard',
    icon: <DashboardIcon />,
  },
  // {
  //   kind: 'header',
  //   title: 'Monitoring',
  // },
  // {
  //   segment: 'agents',
  //   title: 'Agents',
  //   icon: <GroupIcon />,
  // },
  // {
  //   segment: 'calls',
  //   title: 'Active Calls',
  //   icon: <PhoneInTalkIcon />,
  // },
  // {
  //   kind: 'header',
  //   title: 'Analytics',
  // },
  // {
  //   segment: 'reports',
  //   title: 'Reports',
  //   icon: <BarChartIcon />,
  // },
  // {
  //   kind: 'header',
  //   title: 'System',
  // },
  // {
  //   segment: 'settings',
  //   title: 'Settings',
  //   icon: <SettingsIcon />,
  // },
];

// Logo component for sidebar
function AgentMonitorLogo() {
  return <PhoneIcon sx={{ fontSize: 32 }} />;
}

// Create themes for both light and dark modes
const lightTheme = createTheme(getMPTheme('light'));
const darkTheme = createTheme(getMPTheme('dark'));

// Combined theme object for @toolpad/core
const theme = {
  light: lightTheme,
  dark: darkTheme,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" data-toolpad-color-scheme="light" suppressHydrationWarning>
      <head>
        <title>Agent Monitor - Call Center Dashboard</title>
      </head>
      <body style={{ margin: 0 }} suppressHydrationWarning>
        <AppRouterCacheProvider options={{ enableCssLayer: true }}>
          <Suspense fallback={null}>
            <NextAppProvider
              theme={theme}
              navigation={NAVIGATION}
              branding={{
                logo: <AgentMonitorLogo />,
                title: 'Agent Monitor',
                homeUrl: '/',
              }}
            >
              {children}
            </NextAppProvider>
          </Suspense>
        </AppRouterCacheProvider>
      </body>
    </html>
  );
}
