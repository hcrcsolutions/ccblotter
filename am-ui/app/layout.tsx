'use client';

import * as React from 'react';
import { Suspense } from 'react';
import { usePathname } from 'next/navigation';
import { NextAppProvider } from '@toolpad/core/nextjs';
import { AppRouterCacheProvider } from '@mui/material-nextjs/v15-appRouter';
import type { Navigation } from '@toolpad/core/AppProvider';
import { createTheme } from '@mui/material/styles';
import getMPTheme from '@/theme/getMPTheme';
import GroupIcon from '@mui/icons-material/Group';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import PhoneIcon from '@mui/icons-material/Phone';

// Navigation configuration for sidebar
const NAVIGATION: Navigation = [
  {
    segment: 'agents',
    title: 'Agents',
    icon: <GroupIcon />,
  },
  {
    segment: 'calls',
    title: 'Calls',
    icon: <PhoneInTalkIcon />,
  },
];

// Page titles based on pathname
const PAGE_TITLES: Record<string, string> = {
  '/agents': 'Agents',
  '/calls': 'Calls',
};

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

function AppProviderWithDynamicTitle({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const pageTitle = PAGE_TITLES[pathname] || 'Agent Monitor';

  return (
    <NextAppProvider
      theme={theme}
      navigation={NAVIGATION}
      branding={{
        logo: <AgentMonitorLogo />,
        title: pageTitle,
        homeUrl: '/',
      }}
    >
      {children}
    </NextAppProvider>
  );
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" data-toolpad-color-scheme="light" suppressHydrationWarning>
      <head>
        <title>Agent Monitor - Call Center Dashboard</title>
        <style>{`
          /* Branding title color - theme aware */
          .MuiTypography-h6 {
            color: #1a1a1a !important;
          }
          [data-toolpad-color-scheme="dark"] .MuiTypography-h6 {
            color: #f5f5f5 !important;
          }
        `}</style>
      </head>
      <body style={{ margin: 0 }} suppressHydrationWarning>
        <AppRouterCacheProvider options={{ enableCssLayer: true }}>
          <Suspense fallback={null}>
            <AppProviderWithDynamicTitle>
              {children}
            </AppProviderWithDynamicTitle>
          </Suspense>
        </AppRouterCacheProvider>
      </body>
    </html>
  );
}
