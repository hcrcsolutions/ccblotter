'use client';

import * as React from 'react';
import { memo } from 'react';
import { Handle, Position } from '@xyflow/react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import CableIcon from '@mui/icons-material/Cable';
import BuildIcon from '@mui/icons-material/Build';
import { useThemeContext } from '../../context/ThemeContext';
import { SERVER_TYPE_COLORS, SERVER_HEALTH_COLORS, getStatusColors, getLatencyQualityColor, CAPACITY_THRESHOLDS } from '../../lib/statusColors';
import type { InfrastructureNode } from '../../types';

interface TrunkNodeProps {
  data: InfrastructureNode;
}

function TrunkNodeComponent({ data }: TrunkNodeProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  const serverColors = getStatusColors(SERVER_TYPE_COLORS.TRUNK, isDarkMode);
  const healthColorSet = SERVER_HEALTH_COLORS[data.healthStatus] || SERVER_HEALTH_COLORS.UNKNOWN;
  const healthColors = getStatusColors(healthColorSet, isDarkMode);

  const capacityPercent = data.maxSessions > 0
    ? (data.activeSessions / data.maxSessions) * 100
    : 0;

  const latencyColors = data.metrics
    ? getLatencyQualityColor(data.metrics.latencyMs, isDarkMode)
    : null;

  return (
    <>
      <Box
        sx={{
          width: 180,
          height: 130,
          p: 1.5,
          borderRadius: 2,
          bgcolor: healthColors.bg,
          border: data.maintenanceMode ? '2px dashed' : '2px solid',
          borderColor: data.maintenanceMode
            ? (isDarkMode ? 'hsl(45, 80%, 50%)' : 'hsl(45, 80%, 40%)')
            : serverColors.text,
          cursor: 'pointer',
          transition: 'box-shadow 0.2s ease',
          overflow: 'hidden',
          position: 'relative',
          '&:hover': {
            boxShadow: 3,
          },
        }}
      >
        {/* Maintenance Mode Indicator */}
        {data.maintenanceMode && (
          <Box
            sx={{
              position: 'absolute',
              top: 4,
              right: 4,
              width: 20,
              height: 20,
              borderRadius: '50%',
              bgcolor: isDarkMode ? 'hsl(45, 80%, 35%)' : 'hsl(45, 90%, 85%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <BuildIcon sx={{ fontSize: 12, color: isDarkMode ? 'hsl(45, 90%, 70%)' : 'hsl(45, 80%, 35%)' }} />
          </Box>
        )}

        {/* Header: Icon + Carrier Name */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
          <CableIcon sx={{ color: serverColors.text, fontSize: 20 }} />
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
            {data.carrierName || data.hostname}
          </Typography>
        </Box>

        {/* Trunk Group Badge */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.5 }}>
          <Typography variant="caption" sx={{ color: serverColors.text }}>
            {data.trunkGroup === 'primary' ? 'Primary' : 'Backup'}
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

        {/* Latency Display */}
        {data.metrics && (
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Typography variant="caption" sx={{ color: serverColors.text }}>
              Latency
            </Typography>
            <Typography
              variant="caption"
              sx={{
                fontWeight: 600,
                color: latencyColors?.text || serverColors.text,
              }}
            >
              {data.metrics.latencyMs}ms
            </Typography>
          </Box>
        )}

        {/* Capacity */}
        <Box>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Typography variant="caption" sx={{ color: serverColors.text }}>
              Capacity
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
                bgcolor: capacityPercent > CAPACITY_THRESHOLDS.CRITICAL
                  ? 'error.main'
                  : capacityPercent > CAPACITY_THRESHOLDS.WARNING
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

export const TrunkNode = memo(TrunkNodeComponent);
