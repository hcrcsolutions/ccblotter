'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import QueueIcon from '@mui/icons-material/Queue';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import WarningIcon from '@mui/icons-material/Warning';
import { useWebSocketContext } from '../../context/WebSocketContext';
import { ErrorBanner } from '../../components/ErrorBanner/ErrorBanner';
import { SummaryCard } from '../../components/SummaryCard/SummaryCard';
import { QueuedCallGrid } from '../../components/QueuedCallGrid/QueuedCallGrid';
import { ActiveCallGrid } from '../../components/ActiveCallGrid/ActiveCallGrid';
import type { QueueStats } from '../../types';
import { formatDuration } from '../../lib/formatters';

export default function CallsPage() {
  const { systemStatus, connectionState } = useWebSocketContext();

  const [queueStats, setQueueStats] = React.useState<QueueStats>({
    queuedCount: 0,
    avgWaitSeconds: 0,
    longestWaitSeconds: 0,
  });
  const [activeCallCount, setActiveCallCount] = React.useState(0);

  const handleQueueMetadata = React.useCallback((meta: Record<string, unknown>) => {
    setQueueStats({
      queuedCount: (meta.queuedCount as number) || 0,
      avgWaitSeconds: (meta.avgWaitSeconds as number) || 0,
      longestWaitSeconds: (meta.longestWaitSeconds as number) || 0,
    });
  }, []);

  const handleCallsMetadata = React.useCallback((meta: Record<string, unknown>) => {
    setActiveCallCount((meta.activeCallCount as number) || 0);
  }, []);

  const showError = !systemStatus.redisConnected || connectionState === 'error';

  return (
    <Box sx={{ p: 3, height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      {/* Error Banner */}
      {showError && (
        <ErrorBanner
          redisConnected={systemStatus.redisConnected}
          connectionState={connectionState}
          errorMessage={systemStatus.errorMessage}
        />
      )}

      {/* Content */}
      {!showError && (
        <>
          {/* Summary Cards */}
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 3 }}>
            <SummaryCard
              title="In Queue"
              value={queueStats.queuedCount}
              icon={<QueueIcon sx={{ fontSize: 32 }} />}
              color="hsl(210, 98%, 42%)"
              bgColor="hsl(210, 100%, 95%)"
              width={250}
            />
            <SummaryCard
              title="Active Calls"
              value={activeCallCount}
              icon={<PhoneInTalkIcon sx={{ fontSize: 32 }} />}
              color="hsl(120, 59%, 30%)"
              bgColor="hsl(120, 80%, 95%)"
              width={250}
            />
            <SummaryCard
              title="Avg Wait"
              value={formatDuration(queueStats.avgWaitSeconds)}
              icon={<AccessTimeIcon sx={{ fontSize: 32 }} />}
              color="hsl(45, 90%, 40%)"
              bgColor="hsl(45, 100%, 95%)"
              width={250}
            />
            <SummaryCard
              title="Longest Wait"
              value={formatDuration(queueStats.longestWaitSeconds)}
              icon={<WarningIcon sx={{ fontSize: 32 }} />}
              color={queueStats.longestWaitSeconds > 120 ? 'hsl(0, 90%, 40%)' : 'hsl(220, 20%, 40%)'}
              bgColor={queueStats.longestWaitSeconds > 120 ? 'hsl(0, 100%, 95%)' : 'hsl(220, 20%, 95%)'}
              width={250}
            />
          </Box>

          {/* Grids */}
          <Box sx={{ display: 'flex', gap: 3, flex: 1, minHeight: 0 }}>
            {/* Queued Calls */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Queued Calls ({queueStats.queuedCount})
              </Typography>
              <Box sx={{ flex: 1 }}>
                <QueuedCallGrid onMetadata={handleQueueMetadata} />
              </Box>
            </Box>

            {/* Active Calls */}
            <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Active Calls ({activeCallCount})
              </Typography>
              <Box sx={{ flex: 1 }}>
                <ActiveCallGrid onMetadata={handleCallsMetadata} />
              </Box>
            </Box>
          </Box>
        </>
      )}
    </Box>
  );
}
