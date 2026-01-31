'use client';

import * as React from 'react';
import { useState, useEffect } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import Divider from '@mui/material/Divider';
import DnsIcon from '@mui/icons-material/Dns';
import VideocamIcon from '@mui/icons-material/Videocam';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import DeviceHubIcon from '@mui/icons-material/DeviceHub';
import PushPinIcon from '@mui/icons-material/PushPin';
import PushPinOutlinedIcon from '@mui/icons-material/PushPinOutlined';
import { useThemeContext } from '../../context/ThemeContext';
import { SERVER_TYPE_COLORS, SERVER_HEALTH_COLORS, getStatusColors } from '../../lib/statusColors';
import type { InfrastructureNode, InfrastructureEdge } from '../../types';

interface ServerDetailDialogProps {
  open: boolean;
  server: InfrastructureNode | null;
  edges: InfrastructureEdge[];
  onClose: () => void;
  onPin?: () => void;
  isPinned?: boolean;
}

/**
 * Format uptime from ISO timestamp to human-readable duration.
 */
function formatUptime(startTime: string): string {
  const start = new Date(startTime).getTime();
  const now = Date.now();
  const seconds = Math.floor((now - start) / 1000);

  if (seconds < 0) return '0s';

  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  const parts: string[] = [];
  if (days > 0) parts.push(`${days}d`);
  if (hours > 0) parts.push(`${hours}h`);
  if (minutes > 0) parts.push(`${minutes}m`);
  if (parts.length === 0 || (days === 0 && hours === 0)) parts.push(`${secs}s`);

  return parts.join(' ');
}

export function ServerDetailDialog({
  open,
  server,
  edges,
  onClose,
  onPin,
  isPinned = false,
}: ServerDetailDialogProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';
  const [uptime, setUptime] = useState('');

  // Count connected servers based on type
  const connectedMediaCount = server?.type === 'SIP'
    ? edges.filter(e => e.sourceId === server.id).length
    : 0;

  const connectedSipCount = server?.type === 'MEDIA'
    ? edges.filter(e => e.targetId === server.id).length
    : 0;

  // Update uptime every second when dialog is open
  useEffect(() => {
    if (!open || !server) return;

    const updateUptime = () => {
      setUptime(formatUptime(server.startTime));
    };

    updateUptime();
    const interval = setInterval(updateUptime, 1000);

    return () => clearInterval(interval);
  }, [open, server]);

  if (!server) return null;

  const serverTypeColors = getStatusColors(SERVER_TYPE_COLORS[server.type], isDarkMode);
  const healthColorSet = SERVER_HEALTH_COLORS[server.healthStatus] || SERVER_HEALTH_COLORS.UNKNOWN;
  const healthColors = getStatusColors(healthColorSet, isDarkMode);

  const capacityPercent = server.maxSessions > 0
    ? (server.activeSessions / server.maxSessions) * 100
    : 0;

  const Icon = server.type === 'SIP' ? DnsIcon : VideocamIcon;
  const serverTypeLabel = server.type === 'SIP' ? 'SIP Server' : 'Media Server';

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 40,
              height: 40,
              borderRadius: 1,
              bgcolor: serverTypeColors.bg,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon sx={{ color: serverTypeColors.text }} />
          </Box>
          <Box sx={{ flex: 1 }}>
            <Typography variant="h6" sx={{ lineHeight: 1.2 }}>
              {server.hostname}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {serverTypeLabel}
            </Typography>
          </Box>
          <Chip
            label={healthColorSet.label}
            size="small"
            sx={{
              bgcolor: healthColors.bg,
              color: healthColors.text,
            }}
          />
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          {/* IP Address */}
          <Box>
            <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
              IP Address
            </Typography>
            <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
              {server.ipAddress}
            </Typography>
          </Box>

          {/* Uptime */}
          <Box>
            <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
              Uptime
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <AccessTimeIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
              <Typography variant="body1">
                {uptime}
              </Typography>
            </Box>
            <Typography variant="caption" color="text.secondary">
              Started: {new Date(server.startTime).toLocaleString()}
            </Typography>
          </Box>

          {/* Connected Media Servers (SIP only) */}
          {server.type === 'SIP' && (
            <Box>
              <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                Connected Media Servers
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <DeviceHubIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="h4" sx={{ fontWeight: 500 }}>
                  {connectedMediaCount}
                </Typography>
              </Box>
            </Box>
          )}

          {/* Connected SIP Servers (Media only) */}
          {server.type === 'MEDIA' && (
            <Box>
              <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                Connected SIP Servers
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <DeviceHubIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="h4" sx={{ fontWeight: 500 }}>
                  {connectedSipCount}
                </Typography>
              </Box>
            </Box>
          )}

          {/* Active Sessions */}
          <Box>
            <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
              Active Sessions
            </Typography>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="h4" sx={{ fontWeight: 500 }}>
                {server.activeSessions}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                / {server.maxSessions} max
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={capacityPercent}
              sx={{
                height: 8,
                borderRadius: 1,
                '& .MuiLinearProgress-bar': {
                  bgcolor: capacityPercent > 80
                    ? 'error.main'
                    : capacityPercent > 60
                      ? 'warning.main'
                      : 'success.main',
                },
              }}
            />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
              {capacityPercent.toFixed(1)}% capacity
            </Typography>
          </Box>
        </Box>
      </DialogContent>

      <DialogActions sx={{ justifyContent: 'space-between', px: 3 }}>
        {onPin ? (
          <Button
            onClick={onPin}
            startIcon={isPinned ? <PushPinIcon /> : <PushPinOutlinedIcon />}
            color={isPinned ? 'primary' : 'inherit'}
            disabled={isPinned}
          >
            {isPinned ? 'Pinned' : 'Pin to Filter'}
          </Button>
        ) : (
          <Box />
        )}
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
