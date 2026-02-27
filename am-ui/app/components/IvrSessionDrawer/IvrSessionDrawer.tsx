'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import CloseIcon from '@mui/icons-material/Close';
import { useTheme } from '@mui/material/styles';
import type { IvrSessionRow, IvrStep, IvrState, GridResponse } from '../../types';
import { IVR_STATE_COLORS } from '../../lib/statusColors';
import { formatDuration, calculateDuration } from '../../lib/formatters';
import { getApiBaseUrl } from '../../lib/settings';
import { IvrTimeline } from '../IvrTimeline/IvrTimeline';
import { useAudioPlayback } from '../../hooks/useAudioPlayback';

export interface IvrSessionDrawerProps {
  sessionId: string | null;
  onClose: () => void;
}

const DRAWER_WIDTH = 480;

export function IvrSessionDrawer({ sessionId, onClose }: IvrSessionDrawerProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  const [session, setSession] = React.useState<IvrSessionRow | null>(null);
  const [steps, setSteps] = React.useState<IvrStep[]>([]);
  const [loading, setLoading] = React.useState(false);
  const { playingStepId, play, stop } = useAudioPlayback();

  const isLive = session?.state === 'ACTIVE' || session?.state === 'AUTHENTICATING';

  // Fetch session + steps when sessionId changes
  React.useEffect(() => {
    if (!sessionId) {
      stop();
      setSession(null);
      setSteps([]);
      return;
    }
    stop();

    let cancelled = false;

    async function fetchData() {
      setLoading(true);
      try {
        const apiUrl = getApiBaseUrl();

        // Fetch session data via grid query (filter by id)
        const [sessionRes, stepsRes] = await Promise.all([
          fetch(`${apiUrl}/ivr-sessions/query`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              startRow: 0,
              endRow: 100,
              sortModel: [],
              filterModel: {
                id: { filterType: 'text', type: 'equals', filter: sessionId },
              },
            }),
          }),
          fetch(`${apiUrl}/ivr-sessions/${sessionId}/steps`),
        ]);

        if (!cancelled) {
          if (sessionRes.ok) {
            const gridData = (await sessionRes.json()) as GridResponse<IvrSessionRow>;
            setSession(gridData.rows[0] ?? null);
          }
          if (stepsRes.ok) {
            setSteps(await stepsRes.json());
          }
        }
      } catch (err) {
        console.error('Failed to fetch IVR session data:', err);
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    fetchData();

    return () => {
      cancelled = true;
    };
  }, [sessionId, stop]);

  // Poll session + steps every 2s for live sessions
  React.useEffect(() => {
    if (!sessionId || !isLive) {
      return;
    }

    const interval = setInterval(async () => {
      try {
        const apiUrl = getApiBaseUrl();
        const [sessionRes, stepsRes] = await Promise.all([
          fetch(`${apiUrl}/ivr-sessions/query`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              startRow: 0,
              endRow: 100,
              sortModel: [],
              filterModel: {
                id: { filterType: 'text', type: 'equals', filter: sessionId },
              },
            }),
          }),
          fetch(`${apiUrl}/ivr-sessions/${sessionId}/steps`),
        ]);

        if (sessionRes.ok) {
          const gridData = (await sessionRes.json()) as GridResponse<IvrSessionRow>;
          if (gridData.rows[0]) {
            setSession(gridData.rows[0]);
          }
        }
        if (stepsRes.ok) {
          setSteps(await stepsRes.json());
        }
      } catch {
        // ignore polling errors
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [sessionId, isLive]);

  // Live duration ticker
  const [, setTick] = React.useState(0);
  React.useEffect(() => {
    if (!isLive) return;
    const interval = setInterval(() => setTick(t => t + 1), 1000);
    return () => clearInterval(interval);
  }, [isLive]);

  const stateColors = session?.state
    ? IVR_STATE_COLORS[session.state as IvrState]
    : null;
  const stateTheme = stateColors
    ? isDark ? stateColors.dark : stateColors.light
    : null;

  const duration = session?.startTime
    ? session.endTime
      ? Math.floor((new Date(session.endTime).getTime() - new Date(session.startTime).getTime()) / 1000)
      : calculateDuration(session.startTime)
    : null;

  return (
    <Drawer
      anchor="right"
      open={!!sessionId}
      onClose={onClose}
      sx={{
        '& .MuiDrawer-paper': {
          width: DRAWER_WIDTH,
          boxSizing: 'border-box',
        },
      }}
    >
      {loading && !session ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress size={32} />
        </Box>
      ) : session ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
          {/* Header */}
          <Box
            sx={{
              p: 2,
              borderBottom: 1,
              borderColor: 'divider',
              display: 'flex',
              alignItems: 'flex-start',
              gap: 1,
            }}
          >
            <Box sx={{ flex: 1 }}>
              <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>
                {session.originator}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <Typography variant="body2" color="text.secondary">
                  {session.flowId}
                </Typography>
                {stateColors && stateTheme && (
                  <Chip
                    label={stateColors.label}
                    size="small"
                    sx={{
                      backgroundColor: stateTheme.bg,
                      color: stateTheme.text,
                      fontWeight: 600,
                      fontSize: '0.75rem',
                      height: 24,
                      border: '1px solid',
                      borderColor: stateTheme.text,
                    }}
                  />
                )}
                <Typography variant="body2" color="text.secondary">
                  {formatDuration(duration)}
                </Typography>
              </Box>
            </Box>
            <IconButton onClick={onClose} size="small" sx={{ mt: -0.5 }}>
              <CloseIcon />
            </IconButton>
          </Box>

          {/* Body: Timeline */}
          <Box sx={{ flex: 1, overflow: 'auto', py: 1 }}>
            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
                <CircularProgress size={32} />
              </Box>
            ) : (
              <IvrTimeline
                steps={steps}
                playingStepId={playingStepId}
                onPlayAudio={play}
                onStopAudio={stop}
              />
            )}
          </Box>
        </Box>
      ) : null}
    </Drawer>
  );
}
