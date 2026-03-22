'use client';

import * as React from 'react';
import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import VerifiedIcon from '@mui/icons-material/Verified';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import type { ScenarioStep, ActorRole } from '../../types';
import { ACTION_DEP_REQUIREMENTS, ACTION_LABELS, ROLE_COLORS } from './scenarioConstants';

export interface StepNodeData {
  step: ScenarioStep;
  actorName: string;
  actorRole: ActorRole;
  assertionCount: number;
  hasDepError?: boolean;
  [key: string]: unknown;
}

interface ScenarioStepNodeProps {
  data: StepNodeData;
  selected?: boolean;
}

function formatTime(ms: number): string {
  if (ms < 1000) {
    return `${ms}ms`;
  }
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainingSec = seconds % 60;
  return remainingSec > 0 ? `${minutes}m ${remainingSec}s` : `${minutes}m`;
}

function ScenarioStepNodeComponent({ data, selected }: ScenarioStepNodeProps) {
  const { step, actorRole, assertionCount, hasDepError } = data;
  const roleColor = ROLE_COLORS[actorRole];
  const depReq = ACTION_DEP_REQUIREMENTS[step.action];

  return (
    <>
      {/* Top/bottom handles — same-lane (sequence) connections only */}
      <Handle type="source" position={Position.Top} id="top" />
      <Handle type="source" position={Position.Bottom} id="bottom" />
      {/* Left/right handles — cross-lane connections */}
      <Handle type="source" position={Position.Left} id="left-top" style={{ top: '30%' }} />
      <Handle type="source" position={Position.Left} id="left-bottom" style={{ top: '70%' }} />
      <Handle type="source" position={Position.Right} id="right-top" style={{ top: '30%' }} />
      <Handle type="source" position={Position.Right} id="right-bottom" style={{ top: '70%' }} />
      <Box
        sx={{
          width: 180,
          p: 1,
          borderRadius: 2,
          bgcolor: hasDepError ? '#fce4ec' : 'background.paper',
          border: '2px solid',
          borderColor: hasDepError ? '#e57373' : selected ? 'primary.main' : 'divider',
          borderLeft: `4px solid ${roleColor}`,
          cursor: 'pointer',
          transition: 'box-shadow 0.2s ease, background-color 0.2s ease',
          '&:hover': {
            boxShadow: 3,
          },
        }}
      >
        {/* Action label */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
          <Typography
            variant="subtitle2"
            sx={{
              fontWeight: 600,
              fontSize: '0.8rem',
              flex: 1,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {ACTION_LABELS[step.action] || step.action}
          </Typography>
          {hasDepError && depReq && (
            <Tooltip title={`Requires: ${depReq.label}`} arrow>
              <ErrorOutlineIcon sx={{ fontSize: 14, color: '#e57373' }} />
            </Tooltip>
          )}
          {assertionCount > 0 && (
            <VerifiedIcon sx={{ fontSize: 14, color: 'info.main' }} />
          )}
        </Box>

        {/* Delay badge — only shown when delay > 0 */}
        {step.delayMs > 0 && (
          <Chip
            label={`wait ${formatTime(step.delayMs)}`}
            size="small"
            sx={{
              height: 18,
              fontSize: '0.6rem',
              fontWeight: 600,
            }}
          />
        )}
      </Box>
    </>
  );
}

export const ScenarioStepNode = memo(ScenarioStepNodeComponent);
