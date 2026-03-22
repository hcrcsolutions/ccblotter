'use client';

import * as React from 'react';
import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import type { ActorRole } from '../../types';
import { ROLE_COLORS } from './scenarioConstants';

export interface BookendNodeData {
  variant: 'start' | 'end';
  actorId: string;
  actorRole: ActorRole;
  [key: string]: unknown;
}

function ScenarioBookendNodeComponent({ data }: { data: BookendNodeData }) {
  const { variant, actorRole } = data;
  const roleColor = ROLE_COLORS[actorRole];
  const isStart = variant === 'start';

  return (
    <>
      {isStart ? (
        <Handle type="source" position={Position.Bottom} id="bottom" />
      ) : (
        <Handle type="source" position={Position.Top} id="top" />
      )}
      <Box sx={{ width: 180, display: 'flex', justifyContent: 'center' }}>
        <Box
          sx={{
            px: 2,
            py: 0.25,
            borderRadius: 10,
            bgcolor: roleColor,
            color: '#fff',
            opacity: 0.85,
          }}
        >
          <Typography variant="caption" sx={{ fontWeight: 600, fontSize: '0.7rem' }}>
            {isStart ? 'Start' : 'End'}
          </Typography>
        </Box>
      </Box>
    </>
  );
}

export const ScenarioBookendNode = memo(ScenarioBookendNodeComponent);
