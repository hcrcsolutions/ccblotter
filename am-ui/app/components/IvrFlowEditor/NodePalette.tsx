'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import DialpadIcon from '@mui/icons-material/Dialpad';
import ListIcon from '@mui/icons-material/List';
import PhoneForwardedIcon from '@mui/icons-material/PhoneForwarded';
import CallEndIcon from '@mui/icons-material/CallEnd';
import DataObjectIcon from '@mui/icons-material/DataObject';
import ForkRightIcon from '@mui/icons-material/ForkRight';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import ShortcutIcon from '@mui/icons-material/Shortcut';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import HttpIcon from '@mui/icons-material/Http';
import MicIcon from '@mui/icons-material/Mic';
import PsychologyIcon from '@mui/icons-material/Psychology';
import { NODE_GROUPS, getNodeColor, getNodeLabel } from './nodeDefaults';
import type { IvrNodeType } from '../../types';

const NODE_ICONS: Record<IvrNodeType, React.ReactNode> = {
  PLAY_PROMPT: <VolumeUpIcon sx={{ fontSize: 16 }} />,
  COLLECT_DTMF: <DialpadIcon sx={{ fontSize: 16 }} />,
  MENU: <ListIcon sx={{ fontSize: 16 }} />,
  TRANSFER: <PhoneForwardedIcon sx={{ fontSize: 16 }} />,
  HANGUP: <CallEndIcon sx={{ fontSize: 16 }} />,
  SET_VARIABLE: <DataObjectIcon sx={{ fontSize: 16 }} />,
  CONDITION: <ForkRightIcon sx={{ fontSize: 16 }} />,
  SUB_FLOW: <AccountTreeIcon sx={{ fontSize: 16 }} />,
  GO_TO: <ShortcutIcon sx={{ fontSize: 16 }} />,
  WAIT: <HourglassEmptyIcon sx={{ fontSize: 16 }} />,
  HTTP_REQUEST: <HttpIcon sx={{ fontSize: 16 }} />,
  ASR_COLLECT: <MicIcon sx={{ fontSize: 16 }} />,
  NLU_INTENT: <PsychologyIcon sx={{ fontSize: 16 }} />,
};

interface NodePaletteProps {
  onAddNode: (type: IvrNodeType) => void;
}

export function NodePalette({ onAddNode }: NodePaletteProps) {
  return (
    <Box
      sx={{
        width: 180,
        borderRight: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper',
        overflow: 'auto',
        p: 1,
      }}
    >
      <Typography variant="caption" sx={{ fontWeight: 600, mb: 1, display: 'block', px: 0.5 }}>
        Node Palette
      </Typography>
      {NODE_GROUPS.map((group, gi) => (
        <Box key={group.label}>
          {gi > 0 && <Divider sx={{ my: 1 }} />}
          <Typography
            variant="caption"
            sx={{ color: 'text.secondary', px: 0.5, display: 'block', mb: 0.5 }}
          >
            {group.label}
          </Typography>
          {group.types.map((type) => (
            <Box
              key={type}
              onClick={() => onAddNode(type)}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                px: 1,
                py: 0.5,
                borderRadius: 1,
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' },
                mb: 0.25,
              }}
            >
              <Box sx={{ color: getNodeColor(type), display: 'flex' }}>{NODE_ICONS[type]}</Box>
              <Typography variant="caption" sx={{ fontSize: '0.7rem' }}>
                {getNodeLabel(type)}
              </Typography>
            </Box>
          ))}
        </Box>
      ))}
    </Box>
  );
}
