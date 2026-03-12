'use client';

import * as React from 'react';
import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import DialpadIcon from '@mui/icons-material/Dialpad';
import ListIcon from '@mui/icons-material/List';
import PhoneForwardedIcon from '@mui/icons-material/PhoneForwarded';
import CallEndIcon from '@mui/icons-material/CallEnd';
import DataObjectIcon from '@mui/icons-material/DataObject';
import ForkRightIcon from '@mui/icons-material/ForkRight';
import AccountTreeIcon from '@mui/icons-material/AccountTree';

import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import HttpIcon from '@mui/icons-material/Http';
import MicIcon from '@mui/icons-material/Mic';
import PsychologyIcon from '@mui/icons-material/Psychology';
import { getNodeColor } from '../nodeDefaults';
import type { IvrNodeType } from '../../../types';

const NODE_ICONS: Record<IvrNodeType, React.ReactNode> = {
  PLAY_PROMPT: <VolumeUpIcon sx={{ fontSize: 18 }} />,
  COLLECT_DTMF: <DialpadIcon sx={{ fontSize: 18 }} />,
  MENU: <ListIcon sx={{ fontSize: 18 }} />,
  TRANSFER: <PhoneForwardedIcon sx={{ fontSize: 18 }} />,
  HANGUP: <CallEndIcon sx={{ fontSize: 18 }} />,
  SET_VARIABLE: <DataObjectIcon sx={{ fontSize: 18 }} />,
  CONDITION: <ForkRightIcon sx={{ fontSize: 18 }} />,
  SUB_FLOW: <AccountTreeIcon sx={{ fontSize: 18 }} />,

  WAIT: <HourglassEmptyIcon sx={{ fontSize: 18 }} />,
  HTTP_REQUEST: <HttpIcon sx={{ fontSize: 18 }} />,
  ASR_COLLECT: <MicIcon sx={{ fontSize: 18 }} />,
  NLU_INTENT: <PsychologyIcon sx={{ fontSize: 18 }} />,
};

interface IvrNodeProps {
  data: Record<string, unknown>;
  selected?: boolean;
}

function IvrNodeComponent({ data, selected }: IvrNodeProps) {
  const nodeType = data.type as IvrNodeType;
  const label = (data.label as string) || nodeType;
  const color = getNodeColor(nodeType);
  const icon = NODE_ICONS[nodeType];

  return (
    <>
      <Handle type="target" position={Position.Top} id="top" />
      <Handle type="target" position={Position.Left} id="left" />
      <Handle type="source" position={Position.Right} id="right" />
      <Box
        sx={{
          width: 160,
          minHeight: 60,
          p: 1,
          borderRadius: 2,
          bgcolor: 'background.paper',
          border: '2px solid',
          borderColor: selected ? 'primary.main' : color,
          cursor: 'pointer',
          transition: 'box-shadow 0.2s ease',
          '&:hover': {
            boxShadow: 3,
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
          <Box sx={{ color }}>{icon}</Box>
          <Typography
            variant="subtitle2"
            sx={{
              fontWeight: 600,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              flex: 1,
              fontSize: '0.75rem',
            }}
          >
            {label}
          </Typography>
        </Box>
        <Chip
          label={nodeType.replace(/_/g, ' ')}
          size="small"
          sx={{
            height: 18,
            fontSize: '0.6rem',
            bgcolor: `${color}20`,
            color,
            fontWeight: 600,
          }}
        />
      </Box>
      <Handle type="source" position={Position.Bottom} id="bottom" />
    </>
  );
}

export const IvrNode = memo(IvrNodeComponent);
