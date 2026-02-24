'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import { useWebSocketContext } from '../../context/WebSocketContext';
import { AgentStatusCards } from '../../components/AgentStatusCards/AgentStatusCards';
import { AgentGrid } from '../../components/AgentGrid/AgentGrid';
import { ErrorBanner } from '../../components/ErrorBanner/ErrorBanner';
import type { AgentSummary } from '../../types';

export default function AgentsPage() {
  const { systemStatus, connectionState } = useWebSocketContext();
  const [summary, setSummary] = React.useState<AgentSummary>({
    online: 0, onCall: 0, away: 0, unavailable: 0, total: 0,
  });

  const handleMetadata = React.useCallback((meta: Record<string, unknown>) => {
    setSummary({
      online: (meta.online as number) || 0,
      onCall: (meta.onCall as number) || 0,
      away: (meta.away as number) || 0,
      unavailable: (meta.unavailable as number) || 0,
      total: (meta.total as number) || 0,
    });
  }, []);

  const showError = !systemStatus.redisConnected || connectionState === 'error';

  return (
    <Box sx={{ p: 3, height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
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
            <AgentGrid onMetadata={handleMetadata} />
          </Box>
        </>
      )}
    </Box>
  );
}
