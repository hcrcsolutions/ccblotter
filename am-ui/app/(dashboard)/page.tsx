'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import { useWebSocket } from '../hooks/useWebSocket';
import { AgentStatusCards } from '../components/AgentStatusCards/AgentStatusCards';
import { AgentGrid } from '../components/AgentGrid/AgentGrid';
import { ConnectionStatus } from '../components/ConnectionStatus/ConnectionStatus';
import { ErrorBanner } from '../components/ErrorBanner/ErrorBanner';

export default function DashboardPage() {
  const {
    agents,
    calls,
    summary,
    systemStatus,
    connectionState,
  } = useWebSocket();

  const showError = !systemStatus.redisConnected || connectionState === 'error';

  return (
    <Box sx={{ p: 3, height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      {/* Connection Status */}
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2 }}>
        <ConnectionStatus state={connectionState} />
      </Box>

      {/* Error Banner - fail-closed on Redis unavailability */}
      {showError && (
        <ErrorBanner
          redisConnected={systemStatus.redisConnected}
          connectionState={connectionState}
          errorMessage={systemStatus.errorMessage}
        />
      )}

      {/* Content - only show if no error */}
      {!showError && (
        <>
          {/* Agent Status Cards */}
          <AgentStatusCards summary={summary} />

          {/* Agent Grid */}
          <Box sx={{ mt: 3, flex: 1, minHeight: 0 }}>
            <AgentGrid agents={agents} calls={calls} />
          </Box>
        </>
      )}
    </Box>
  );
}
