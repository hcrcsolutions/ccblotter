'use client';

import * as React from 'react';
import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import DnsIcon from '@mui/icons-material/Dns';
import { useThemeContext } from '../../context/ThemeContext';
import { SERVER_TYPE_COLORS, SERVER_HEALTH_COLORS, getStatusColors } from '../../lib/statusColors';
import type { InfrastructureNode } from '../../types';

interface SipServerNodeProps {
  data: InfrastructureNode;
}

function SipServerNodeComponent({ data }: SipServerNodeProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  const serverColors = getStatusColors(SERVER_TYPE_COLORS.SIP, isDarkMode);
  const healthColorSet = SERVER_HEALTH_COLORS[data.healthStatus] || SERVER_HEALTH_COLORS.UNKNOWN;
  const healthColors = getStatusColors(healthColorSet, isDarkMode);

  const capacityPercent = data.maxSessions > 0
    ? (data.activeSessions / data.maxSessions) * 100
    : 0;

  return (
    <>
      <Handle type="target" position={Position.Left} />
      <Box
        sx={{
          width: 180,
          height: 120,
          p: 1.5,
          borderRadius: 2,
          bgcolor: healthColors.bg,
          border: '2px solid',
          borderColor: serverColors.text,
          cursor: 'pointer',
          transition: 'box-shadow 0.2s ease',
          overflow: 'hidden',
          '&:hover': {
            boxShadow: 3,
          },
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
          <DnsIcon sx={{ color: serverColors.text, fontSize: 20 }} />
          <Typography
            variant="subtitle2"
            sx={{
              color: serverColors.text,
              fontWeight: 600,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              flex: 1,
            }}
          >
            {data.hostname}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="caption" sx={{ color: serverColors.text }}>
            SIP
          </Typography>
          <Chip
            label={healthColorSet.label}
            size="small"
            sx={{
              height: 18,
              fontSize: '0.6rem',
              bgcolor: healthColors.bg,
              color: healthColors.text,
            }}
          />
        </Box>

        <Box sx={{ mt: 1 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Typography variant="caption" sx={{ color: serverColors.text }}>
              Sessions
            </Typography>
            <Typography variant="caption" sx={{ color: serverColors.text, fontWeight: 600 }}>
              {data.activeSessions}/{data.maxSessions}
            </Typography>
          </Box>
          <LinearProgress
            variant="determinate"
            value={capacityPercent}
            sx={{
              height: 6,
              borderRadius: 1,
              bgcolor: isDarkMode ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
              '& .MuiLinearProgress-bar': {
                bgcolor: capacityPercent > 80
                  ? 'error.main'
                  : capacityPercent > 60
                    ? 'warning.main'
                    : 'success.main',
              },
            }}
          />
        </Box>
      </Box>
      <Handle type="source" position={Position.Right} />
    </>
  );
}

export const SipServerNode = memo(SipServerNodeComponent);
