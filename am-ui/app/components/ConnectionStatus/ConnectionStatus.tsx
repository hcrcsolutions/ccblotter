'use client';

import * as React from 'react';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import SyncIcon from '@mui/icons-material/Sync';
import type { ConnectionState } from '../../types';

interface ConnectionStatusProps {
  state: ConnectionState;
}

export function ConnectionStatus({ state }: ConnectionStatusProps) {
  const getStatusConfig = () => {
    switch (state) {
      case 'connected':
        return {
          label: 'Connected',
          color: 'success' as const,
          icon: <CheckCircleIcon />,
        };
      case 'connecting':
        return {
          label: 'Connecting...',
          color: 'default' as const,
          icon: <CircularProgress size={16} />,
        };
      case 'disconnected':
        return {
          label: 'Reconnecting...',
          color: 'warning' as const,
          icon: <SyncIcon sx={{ animation: 'spin 1s linear infinite', '@keyframes spin': { from: { transform: 'rotate(0deg)' }, to: { transform: 'rotate(360deg)' } } }} />,
        };
      case 'error':
        return {
          label: 'Connection Lost',
          color: 'error' as const,
          icon: <ErrorIcon />,
        };
      default:
        return {
          label: 'Unknown',
          color: 'default' as const,
          icon: null,
        };
    }
  };

  const config = getStatusConfig();

  return (
    <Chip
      icon={config.icon || undefined}
      label={config.label}
      color={config.color}
      size="medium"
      sx={{ fontWeight: 500 }}
    />
  );
}
