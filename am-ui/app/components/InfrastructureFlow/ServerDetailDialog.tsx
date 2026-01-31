'use client';

import { memo, useState, useEffect, useMemo } from 'react';
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
import CableIcon from '@mui/icons-material/Cable';
import SecurityIcon from '@mui/icons-material/Security';
import DnsIcon from '@mui/icons-material/Dns';
import VideocamIcon from '@mui/icons-material/Videocam';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import DeviceHubIcon from '@mui/icons-material/DeviceHub';
import PushPinIcon from '@mui/icons-material/PushPin';
import PushPinOutlinedIcon from '@mui/icons-material/PushPinOutlined';
import BuildIcon from '@mui/icons-material/Build';
import type { SvgIconComponent } from '@mui/icons-material';
import { useThemeContext } from '../../context/ThemeContext';
import { SERVER_TYPE_COLORS, SERVER_HEALTH_COLORS, WAIT_TIME_COLORS, getStatusColors, getLatencyQualityColor, getMosQualityColor, CAPACITY_THRESHOLDS, CPU_THRESHOLDS, MEMORY_THRESHOLDS } from '../../lib/statusColors';
import { Sparkline } from './Sparkline';
import type { InfrastructureNode, InfrastructureEdge, InfraServerType, NodeMetrics, SessionBreakdown, TrendDataPoint } from '../../types';

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

interface MetricBoxProps {
  label: string;
  value: string;
  bgColor?: string;
  textColor?: string;
}

const MetricBox = memo(function MetricBox({ label, value, bgColor, textColor }: MetricBoxProps) {
  return (
    <Box sx={{ p: 1.5, borderRadius: 1, bgcolor: bgColor || 'action.hover' }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h6" sx={{ color: textColor }}>{value}</Typography>
    </Box>
  );
});

interface QualityMetricsSectionProps {
  metrics: NodeMetrics;
  isDarkMode: boolean;
}

const QualityMetricsSection = memo(function QualityMetricsSection({ metrics, isDarkMode }: QualityMetricsSectionProps) {
  const latencyColors = getLatencyQualityColor(metrics.latencyMs, isDarkMode);
  const mosColors = getMosQualityColor(metrics.mosScore, isDarkMode);

  return (
    <>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        Quality Metrics
      </Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1.5, mb: 2 }}>
        <MetricBox label="Latency" value={`${metrics.latencyMs}ms`} bgColor={latencyColors.bg} textColor={latencyColors.text} />
        <MetricBox label="Jitter" value={`${metrics.jitterMs}ms`} />
        <MetricBox label="MOS Score" value={(metrics.mosScore / 10).toFixed(1)} bgColor={mosColors.bg} textColor={mosColors.text} />
        <MetricBox label="Packet Loss" value={`${metrics.packetLossPercent.toFixed(2)}%`} />
      </Box>
    </>
  );
});

interface ResourceMetricsSectionProps {
  metrics: NodeMetrics;
}

const ResourceMetricsSection = memo(function ResourceMetricsSection({ metrics }: ResourceMetricsSectionProps) {
  const cpuColor = metrics.cpuPercent > CPU_THRESHOLDS.CRITICAL ? 'error.main' :
                   metrics.cpuPercent > CPU_THRESHOLDS.WARNING ? 'warning.main' : undefined;
  const memColor = metrics.memoryPercent > MEMORY_THRESHOLDS.CRITICAL ? 'error.main' :
                   metrics.memoryPercent > MEMORY_THRESHOLDS.WARNING ? 'warning.main' : undefined;

  return (
    <>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        Resources
      </Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1.5, mb: 2 }}>
        <MetricBox label="CPU" value={`${Math.round(metrics.cpuPercent)}%`} textColor={cpuColor} />
        <MetricBox label="Memory" value={`${Math.round(metrics.memoryPercent)}%`} textColor={memColor} />
      </Box>
    </>
  );
});

interface SessionBreakdownSectionProps {
  breakdown: SessionBreakdown;
  isDarkMode: boolean;
}

const SessionBreakdownSection = memo(function SessionBreakdownSection({ breakdown, isDarkMode }: SessionBreakdownSectionProps) {
  const borderColor = isDarkMode ? 'grey.800' : 'grey.200';
  const cellSx = { display: 'flex', justifyContent: 'space-between', py: 1 };

  return (
    <>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        Session Breakdown
      </Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 0, mb: 2 }}>
        <Box sx={{ ...cellSx, pr: 2, borderBottom: '1px solid', borderRight: '1px solid', borderColor }}>
          <Typography variant="body2" color="text.secondary">Inbound</Typography>
          <Typography variant="body2">{breakdown.inboundSessions}</Typography>
        </Box>
        <Box sx={{ ...cellSx, pl: 2, borderBottom: '1px solid', borderColor }}>
          <Typography variant="body2" color="text.secondary">Outbound</Typography>
          <Typography variant="body2">{breakdown.outboundSessions}</Typography>
        </Box>
        <Box sx={{ ...cellSx, pr: 2, borderBottom: '1px solid', borderRight: '1px solid', borderColor }}>
          <Typography variant="body2" color="text.secondary">IVR</Typography>
          <Typography variant="body2">{breakdown.ivrSessions}</Typography>
        </Box>
        <Box sx={{ ...cellSx, pl: 2, borderBottom: '1px solid', borderColor }}>
          <Typography variant="body2" color="text.secondary">Queue</Typography>
          <Typography variant="body2">{breakdown.queueSessions}</Typography>
        </Box>
        <Box sx={{ ...cellSx, pr: 2, borderRight: '1px solid', borderColor }}>
          <Typography variant="body2" color="text.secondary">Agent</Typography>
          <Typography variant="body2">{breakdown.agentSessions}</Typography>
        </Box>
        <Box sx={{ ...cellSx, pl: 2 }}>
          <Typography variant="body2" color="text.secondary">On Hold</Typography>
          <Typography variant="body2">{breakdown.onHoldSessions}</Typography>
        </Box>
      </Box>
    </>
  );
});

interface TrendSectionProps {
  trendHistory: TrendDataPoint[];
}

const TrendSection = memo(function TrendSection({ trendHistory }: TrendSectionProps) {
  return (
    <>
      <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        Trend (5 min)
      </Typography>
      <Box sx={{ display: 'flex', gap: 3 }}>
        <Box>
          <Typography variant="caption" color="text.secondary">Sessions</Typography>
          <Sparkline data={trendHistory} valueKey="activeSessions" width={100} height={32} />
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary">CPU</Typography>
          <Sparkline data={trendHistory} valueKey="cpuPercent" width={100} height={32} />
        </Box>
      </Box>
    </>
  );
});

// =============================================================================
// MAIN COMPONENT
// =============================================================================

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
  if (Number.isNaN(start)) return 'Unknown';

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
  if (parts.length === 0) parts.push(`${secs}s`);

  return parts.join(' ');
}

/**
 * Server type metadata lookup
 */
const SERVER_TYPE_INFO: Record<InfraServerType, { icon: SvgIconComponent; label: string }> = {
  TRUNK: { icon: CableIcon, label: 'Trunk' },
  SBC: { icon: SecurityIcon, label: 'Session Border Controller' },
  SIP: { icon: DnsIcon, label: 'SIP Server' },
  MEDIA: { icon: VideocamIcon, label: 'Media Server' },
};

/**
 * Get the icon component for a server type.
 */
function getServerIcon(type: InfraServerType): SvgIconComponent {
  return SERVER_TYPE_INFO[type].icon;
}

/**
 * Get the label for a server type.
 */
function getServerTypeLabel(type: InfraServerType): string {
  return SERVER_TYPE_INFO[type].label;
}

export const ServerDetailDialog = memo(function ServerDetailDialog({
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

  // Count connected servers based on type (single pass for efficiency)
  const connectedCounts = useMemo(() => {
    if (!server) return { upstream: 0, downstream: 0 };
    let upstream = 0;
    let downstream = 0;
    for (const edge of edges) {
      if (edge.targetId === server.id) upstream++;
      if (edge.sourceId === server.id) downstream++;
    }
    return { upstream, downstream };
  }, [server, edges]);

  // Update uptime every second when dialog is open
  useEffect(() => {
    if (!open || !server) return;

    const updateUptime = () => {
      setUptime(formatUptime(server.startTime));
    };

    updateUptime();
    const interval = setInterval(updateUptime, 1000);

    return () => clearInterval(interval);
  }, [open, server?.startTime]);

  if (!server) return null;

  const serverTypeColors = getStatusColors(SERVER_TYPE_COLORS[server.type], isDarkMode);
  const healthColorSet = SERVER_HEALTH_COLORS[server.healthStatus] || SERVER_HEALTH_COLORS.UNKNOWN;
  const healthColors = getStatusColors(healthColorSet, isDarkMode);
  const maintenanceColors = getStatusColors(WAIT_TIME_COLORS.warning, isDarkMode);

  const capacityPercent = server.maxSessions > 0
    ? (server.activeSessions / server.maxSessions) * 100
    : 0;

  const Icon = getServerIcon(server.type);
  const serverTypeLabel = getServerTypeLabel(server.type);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      maxWidth="md"
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
              {server.carrierName && ` - ${server.carrierName}`}
              {server.trunkGroup && ` (${server.trunkGroup})`}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            {server.maintenanceMode && (
              <Chip
                icon={<BuildIcon sx={{ fontSize: 14 }} />}
                label="Maintenance"
                size="small"
                sx={{
                  bgcolor: maintenanceColors.bg,
                  color: maintenanceColors.text,
                }}
              />
            )}
            <Chip
              label={healthColorSet.label}
              size="small"
              sx={{
                bgcolor: healthColors.bg,
                color: healthColors.text,
              }}
            />
          </Box>
        </Box>
      </DialogTitle>

      <Divider />

      <DialogContent>
        <Box sx={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {/* Left Column: Basic Info */}
          <Box sx={{ flex: 1, minWidth: 280 }}>
            {/* IP Address */}
            <Box sx={{ mb: 2 }}>
              <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                IP Address
              </Typography>
              <Typography variant="body1" sx={{ fontFamily: 'monospace' }}>
                {server.ipAddress}
              </Typography>
            </Box>

            {/* Datacenter / Region */}
            <Box sx={{ mb: 2 }}>
              <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                Location
              </Typography>
              <Typography variant="body1">
                {server.datacenter?.toUpperCase()} ({server.region})
              </Typography>
            </Box>

            {/* Uptime */}
            <Box sx={{ mb: 2 }}>
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

            {/* Connected Servers */}
            <Box sx={{ mb: 2 }}>
              <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                Connections
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <DeviceHubIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                <Typography variant="body1">
                  {connectedCounts.upstream} upstream, {connectedCounts.downstream} downstream
                </Typography>
              </Box>
            </Box>

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
                    bgcolor: capacityPercent > CAPACITY_THRESHOLDS.CRITICAL
                      ? 'error.main'
                      : capacityPercent > CAPACITY_THRESHOLDS.WARNING
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

          {/* Right Column: Metrics */}
          <Box sx={{ flex: 1, minWidth: 280 }}>
            {server.metrics && (
              <>
                <QualityMetricsSection metrics={server.metrics} isDarkMode={isDarkMode} />
                <ResourceMetricsSection metrics={server.metrics} />
              </>
            )}

            {server.sessionBreakdown && server.activeSessions > 0 && (
              <SessionBreakdownSection breakdown={server.sessionBreakdown} isDarkMode={isDarkMode} />
            )}

            {server.trendHistory && server.trendHistory.length > 1 && (
              <TrendSection trendHistory={server.trendHistory} />
            )}
          </Box>
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3 }}>
        {onPin && (
          <Button
            onClick={onPin}
            startIcon={isPinned ? <PushPinIcon /> : <PushPinOutlinedIcon />}
            color={isPinned ? 'primary' : 'inherit'}
            disabled={isPinned}
            sx={{ mr: 'auto' }}
          >
            {isPinned ? 'Pinned' : 'Pin to Filter'}
          </Button>
        )}
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
});
