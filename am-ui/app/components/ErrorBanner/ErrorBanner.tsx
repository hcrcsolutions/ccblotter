'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Typography from '@mui/material/Typography';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import type { ConnectionState } from '../../types';

interface ErrorBannerProps {
  redisConnected: boolean;
  connectionState: ConnectionState;
  errorMessage: string | null;
}

/**
 * Full-screen error banner for fail-closed behavior.
 *
 * Displayed when:
 * - Redis is unavailable (catastrophic failure)
 * - WebSocket connection is lost and cannot reconnect
 */
export function ErrorBanner({ redisConnected, connectionState, errorMessage }: ErrorBannerProps) {
  const isRedisError = !redisConnected;

  const title = isRedisError
    ? 'Data Source Unavailable'
    : 'Connection Lost';

  const message = isRedisError
    ? 'The system cannot display real-time data because the data source (Redis) is unavailable. Data may be stale or inaccurate. Please contact system administrator.'
    : 'Unable to connect to the server. Please check your network connection and refresh the page.';

  return (
    <Box
      sx={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: 4,
      }}
    >
      <Alert
        severity="error"
        variant="outlined"
        icon={<WarningAmberIcon sx={{ fontSize: 48 }} />}
        sx={{
          maxWidth: 600,
          p: 4,
          borderWidth: 2,
          '& .MuiAlert-icon': {
            alignItems: 'center',
          },
        }}
      >
        <AlertTitle sx={{ fontSize: '1.5rem', fontWeight: 600, mb: 2 }}>
          {title}
        </AlertTitle>
        <Typography variant="body1" sx={{ mb: 2 }}>
          {message}
        </Typography>
        {errorMessage && (
          <Typography
            variant="body2"
            sx={{
              fontFamily: 'monospace',
              backgroundColor: 'action.hover',
              p: 1,
              borderRadius: 1,
              color: 'text.secondary',
            }}
          >
            Error: {errorMessage}
          </Typography>
        )}
        <Typography variant="caption" sx={{ display: 'block', mt: 2, color: 'text.secondary' }}>
          This system operates in fail-closed mode. Data display is disabled to prevent showing stale or inaccurate information.
        </Typography>
      </Alert>
    </Box>
  );
}
