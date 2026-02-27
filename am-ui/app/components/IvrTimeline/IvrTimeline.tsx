'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import LockIcon from '@mui/icons-material/Lock';
import PhoneForwardedIcon from '@mui/icons-material/PhoneForwarded';
import CallEndIcon from '@mui/icons-material/CallEnd';
import AltRouteIcon from '@mui/icons-material/AltRoute';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import ReplayIcon from '@mui/icons-material/Replay';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import IconButton from '@mui/material/IconButton';
import { useTheme } from '@mui/material/styles';
import type { IvrStep, IvrStepType } from '../../types';
import { formatTime } from '../../lib/formatters';

export interface IvrTimelineProps {
  steps: IvrStep[];
  playingStepId?: string | null;
  onPlayAudio?: (stepId: string, audioUrl: string) => void;
  onStopAudio?: () => void;
}

const STEP_TYPE_LABELS: Record<IvrStepType, string> = {
  PLAY: 'Play',
  SAY: 'Say',
  CAPTURE: 'Capture',
  AUTHENTICATE: 'Auth',
  TRANSFER: 'Transfer',
  HANGUP: 'Hangup',
  BRANCH: 'Branch',
};

function isSystemStep(stepType: IvrStepType): boolean {
  return stepType === 'PLAY' || stepType === 'SAY';
}

function isCallerStep(stepType: IvrStepType): boolean {
  return stepType === 'CAPTURE';
}

function isFullWidthStep(stepType: IvrStepType): boolean {
  return stepType === 'AUTHENTICATE' || stepType === 'TRANSFER' || stepType === 'HANGUP';
}

export function IvrTimeline({ steps, playingStepId, onPlayAudio, onStopAudio }: IvrTimelineProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  if (steps.length === 0) {
    return (
      <Box sx={{ p: 3, textAlign: 'center' }}>
        <Typography color="text.secondary">No steps recorded</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, py: 1 }}>
      {steps.map((step, index) => (
        <React.Fragment key={step.id}>
          {step.stepType === 'BRANCH' ? (
            <BranchConnector step={step} />
          ) : isFullWidthStep(step.stepType) ? (
            <FullWidthCard step={step} isDark={isDark} />
          ) : (
            <StepBubble
              step={step}
              isDark={isDark}
              playingStepId={playingStepId}
              onPlayAudio={onPlayAudio}
              onStopAudio={onStopAudio}
            />
          )}

          {/* Latency connector between system prompt and next caller response */}
          {isSystemStep(step.stepType) &&
            index + 1 < steps.length &&
            isCallerStep(steps[index + 1].stepType) && (
              <LatencyConnector latencyMs={steps[index + 1].latencyMs} />
            )}
        </React.Fragment>
      ))}
    </Box>
  );
}

function StepBubble({
  step,
  isDark,
  playingStepId,
  onPlayAudio,
  onStopAudio,
}: {
  step: IvrStep;
  isDark: boolean;
  playingStepId?: string | null;
  onPlayAudio?: (stepId: string, audioUrl: string) => void;
  onStopAudio?: () => void;
}) {
  const isSystem = isSystemStep(step.stepType);
  const isCaller = isCallerStep(step.stepType);
  const hasError = !!step.error;
  const isPlayingAudio = playingStepId === step.id;

  const bubbleBg = hasError
    ? isDark ? 'hsl(0, 40%, 18%)' : 'hsl(0, 90%, 96%)'
    : isSystem
      ? isDark ? 'hsl(210, 30%, 20%)' : 'hsl(210, 60%, 96%)'
      : isDark ? 'hsl(160, 30%, 18%)' : 'hsl(160, 50%, 96%)';

  const borderColor = hasError
    ? isDark ? 'hsl(0, 60%, 40%)' : 'hsl(0, 70%, 60%)'
    : isDark ? 'transparent' : 'transparent';

  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: isCaller ? 'flex-end' : 'flex-start',
        px: 1,
      }}
    >
      <Box
        sx={{
          maxWidth: '85%',
          backgroundColor: bubbleBg,
          borderRadius: 2,
          px: 1.5,
          py: 1,
          border: hasError ? '1px solid' : 'none',
          borderColor,
        }}
      >
        {/* Header: type badge + timestamp */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
          <Chip
            label={STEP_TYPE_LABELS[step.stepType]}
            size="small"
            sx={{
              height: 20,
              fontSize: '0.65rem',
              fontWeight: 600,
              backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)',
            }}
          />
          <Typography variant="caption" color="text.secondary">
            {formatTime(step.startTime)}
          </Typography>
          {step.retryAttempt > 0 && (
            <Chip
              icon={<ReplayIcon sx={{ fontSize: 12 }} />}
              label={`Retry ${step.retryAttempt}`}
              size="small"
              sx={{
                height: 20,
                fontSize: '0.6rem',
                backgroundColor: isDark ? 'hsl(30, 40%, 20%)' : 'hsl(30, 90%, 92%)',
                color: isDark ? 'hsl(30, 70%, 65%)' : 'hsl(30, 70%, 35%)',
              }}
            />
          )}
        </Box>

        {/* Content */}
        {step.prompt && (
          <Typography variant="body2" sx={{ mb: 0.25 }}>
            {step.prompt}
          </Typography>
        )}
        {step.input && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Typography variant="body2" sx={{ fontStyle: 'italic', color: 'text.secondary' }}>
              {isCaller ? step.input : `Input: ${step.input}`}
            </Typography>
            {step.audioUrl && onPlayAudio && onStopAudio && (
              <IconButton
                size="small"
                onClick={() =>
                  isPlayingAudio
                    ? onStopAudio()
                    : onPlayAudio(step.id, step.audioUrl!)
                }
                sx={{
                  width: 28,
                  height: 28,
                  color: isPlayingAudio ? 'primary.main' : 'text.secondary',
                }}
              >
                {isPlayingAudio ? (
                  <StopIcon sx={{ fontSize: 18 }} />
                ) : (
                  <PlayArrowIcon sx={{ fontSize: 18 }} />
                )}
              </IconButton>
            )}
          </Box>
        )}

        {/* Error */}
        {step.error && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}>
            <ErrorOutlineIcon sx={{ fontSize: 14, color: 'error.main' }} />
            <Typography variant="caption" color="error.main">
              {step.error}
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
}

function FullWidthCard({ step, isDark }: { step: IvrStep; isDark: boolean }) {
  const hasError = !!step.error;

  let icon: React.ReactNode;
  let cardBg: string;

  switch (step.stepType) {
    case 'AUTHENTICATE':
      icon = <LockIcon sx={{ fontSize: 18 }} />;
      cardBg = hasError
        ? isDark ? 'hsl(0, 40%, 18%)' : 'hsl(0, 90%, 96%)'
        : isDark ? 'hsl(210, 30%, 18%)' : 'hsl(210, 80%, 96%)';
      break;
    case 'TRANSFER':
      icon = <PhoneForwardedIcon sx={{ fontSize: 18 }} />;
      cardBg = isDark ? 'hsl(270, 30%, 18%)' : 'hsl(270, 60%, 96%)';
      break;
    case 'HANGUP':
      icon = <CallEndIcon sx={{ fontSize: 18 }} />;
      cardBg = isDark ? 'hsl(0, 20%, 18%)' : 'hsl(0, 30%, 96%)';
      break;
    default:
      icon = null;
      cardBg = isDark ? 'hsl(0, 0%, 18%)' : 'hsl(0, 0%, 96%)';
  }

  return (
    <Box sx={{ px: 1 }}>
      <Box
        sx={{
          backgroundColor: cardBg,
          borderRadius: 2,
          px: 2,
          py: 1.5,
          border: hasError ? '1px solid' : '1px solid',
          borderColor: hasError
            ? isDark ? 'hsl(0, 60%, 40%)' : 'hsl(0, 70%, 60%)'
            : isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
          {icon}
          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
            {step.stepName}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
            {formatTime(step.startTime)}
          </Typography>
        </Box>

        {step.prompt && (
          <Typography variant="body2" color="text.secondary">
            {step.prompt}
          </Typography>
        )}

        {step.outcome && (
          <Typography variant="caption" color="text.secondary">
            Outcome: {step.outcome}
          </Typography>
        )}

        {step.error && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}>
            <ErrorOutlineIcon sx={{ fontSize: 14, color: 'error.main' }} />
            <Typography variant="caption" color="error.main">
              {step.error}
            </Typography>
          </Box>
        )}

        {step.retryAttempt > 0 && (
          <Chip
            icon={<ReplayIcon sx={{ fontSize: 12 }} />}
            label={`Attempt ${step.retryAttempt + 1}`}
            size="small"
            sx={{ mt: 0.5, height: 20, fontSize: '0.6rem' }}
          />
        )}
      </Box>
    </Box>
  );
}

function BranchConnector({ step }: { step: IvrStep }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', py: 0.5 }}>
      <AltRouteIcon sx={{ fontSize: 16, color: 'text.disabled', mr: 0.5 }} />
      <Typography variant="caption" color="text.disabled">
        {step.stepName}: {step.outcome}
      </Typography>
    </Box>
  );
}

function LatencyConnector({ latencyMs }: { latencyMs: number }) {
  const isWarning = latencyMs > 500;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', py: 0.25 }}>
      <Box
        sx={{
          width: 1,
          height: 16,
          backgroundColor: isWarning ? 'warning.main' : 'divider',
        }}
      />
      <Typography
        variant="caption"
        sx={{
          ml: 0.5,
          fontSize: '0.6rem',
          color: isWarning ? 'warning.main' : 'text.disabled',
          fontWeight: isWarning ? 600 : 400,
        }}
      >
        {latencyMs}ms
      </Typography>
    </Box>
  );
}
