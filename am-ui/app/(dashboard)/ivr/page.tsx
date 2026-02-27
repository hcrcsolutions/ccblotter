'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import LockIcon from '@mui/icons-material/Lock';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PersonOffIcon from '@mui/icons-material/PersonOff';
import ErrorIcon from '@mui/icons-material/Error';
import { useWebSocketContext } from '../../context/WebSocketContext';
import { ErrorBanner } from '../../components/ErrorBanner/ErrorBanner';
import { SummaryCard } from '../../components/SummaryCard/SummaryCard';
import { IvrSessionGrid } from '../../components/IvrSessionGrid/IvrSessionGrid';
import { IvrSessionDrawer } from '../../components/IvrSessionDrawer/IvrSessionDrawer';

export default function IvrPage() {
  const { systemStatus, connectionState } = useWebSocketContext();

  const [summary, setSummary] = React.useState({
    active: 0,
    authenticating: 0,
    completed: 0,
    abandoned: 0,
    failed: 0,
    total: 0,
  });
  const [selectedSessionId, setSelectedSessionId] = React.useState<string | null>(null);

  const handleMetadata = React.useCallback((meta: Record<string, unknown>) => {
    setSummary({
      active: (meta.active as number) || 0,
      authenticating: (meta.authenticating as number) || 0,
      completed: (meta.completed as number) || 0,
      abandoned: (meta.abandoned as number) || 0,
      failed: (meta.failed as number) || 0,
      total: (meta.total as number) || 0,
    });
  }, []);

  const handleSelectSession = React.useCallback((sessionId: string) => {
    setSelectedSessionId(sessionId);
  }, []);

  const handleCloseDrawer = React.useCallback(() => {
    setSelectedSessionId(null);
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
              title="Active"
              value={summary.active}
              icon={<SmartToyIcon sx={{ fontSize: 32 }} />}
              color="hsl(180, 60%, 30%)"
              bgColor="hsl(180, 70%, 95%)"
              width={200}
            />
            <SummaryCard
              title="Authenticating"
              value={summary.authenticating}
              icon={<LockIcon sx={{ fontSize: 32 }} />}
              color="hsl(210, 100%, 35%)"
              bgColor="hsl(210, 100%, 95%)"
              width={200}
            />
            <SummaryCard
              title="Completed"
              value={summary.completed}
              icon={<CheckCircleIcon sx={{ fontSize: 32 }} />}
              color="hsl(120, 59%, 30%)"
              bgColor="hsl(120, 80%, 95%)"
              width={200}
            />
            <SummaryCard
              title="Abandoned"
              value={summary.abandoned}
              icon={<PersonOffIcon sx={{ fontSize: 32 }} />}
              color="hsl(30, 80%, 30%)"
              bgColor="hsl(30, 100%, 95%)"
              width={200}
            />
            <SummaryCard
              title="Failed"
              value={summary.failed}
              icon={<ErrorIcon sx={{ fontSize: 32 }} />}
              color="hsl(0, 70%, 35%)"
              bgColor="hsl(0, 90%, 95%)"
              width={200}
            />
          </Box>

          {/* Grid */}
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <Typography variant="h6" sx={{ mb: 1 }}>
              IVR Sessions ({summary.total})
            </Typography>
            <Box sx={{ flex: 1 }}>
              <IvrSessionGrid
                onMetadata={handleMetadata}
                onSelectSession={handleSelectSession}
              />
            </Box>
          </Box>

          {/* Session Detail Drawer */}
          <IvrSessionDrawer
            sessionId={selectedSessionId}
            onClose={handleCloseDrawer}
          />
        </>
      )}
    </Box>
  );
}
