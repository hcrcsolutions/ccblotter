'use client';

import { useMemo, memo } from 'react';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';
import CloseIcon from '@mui/icons-material/Close';
import SpeedIcon from '@mui/icons-material/Speed';
import NetworkCheckIcon from '@mui/icons-material/NetworkCheck';
import MemoryIcon from '@mui/icons-material/Memory';
import CallIcon from '@mui/icons-material/Call';
import CallReceivedIcon from '@mui/icons-material/CallReceived';
import CallMadeIcon from '@mui/icons-material/CallMade';
import { useThemeContext } from '../../context/ThemeContext';
import { VisuallyHidden } from '../VisuallyHidden';
import { SERVER_HEALTH_COLORS, getStatusColors, getLatencyQualityColor, CAPACITY_THRESHOLDS, CPU_THRESHOLDS, MEMORY_THRESHOLDS, SESSION_BREAKDOWN_COLORS } from '../../lib/statusColors';
import type { InfrastructureTopology, InfrastructureSummary } from '../../types';

interface MetricsPanelProps {
  open: boolean;
  onClose: () => void;
  topology: InfrastructureTopology;
  summary: InfrastructureSummary;
}

// Simple bar chart component for session breakdown
interface BarChartProps {
  data: { label: string; value: number; color: string }[];
  maxValue: number;
}

const HorizontalBarChart = memo(function HorizontalBarChart({ data, maxValue }: BarChartProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  if (maxValue === 0) return null;

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
      {data.map((item) => (
        <Box key={item.label}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
            <Typography variant="caption" color="text.secondary">
              {item.label}
            </Typography>
            <Typography variant="caption" sx={{ fontWeight: 600 }}>
              {item.value.toLocaleString()}
            </Typography>
          </Box>
          <Box
            sx={{
              height: 8,
              borderRadius: 1,
              bgcolor: isDarkMode ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
              overflow: 'hidden',
            }}
          >
            <Box
              sx={{
                height: '100%',
                width: `${(item.value / maxValue) * 100}%`,
                bgcolor: item.color,
                borderRadius: 1,
                transition: 'width 0.3s ease',
              }}
            />
          </Box>
        </Box>
      ))}
    </Box>
  );
});

// Metric card component
interface MetricCardProps {
  label: string;
  value: string | number;
  subtitle?: string;
  icon: React.ReactNode;
  color?: string;
  bgColor?: string;
}

const MetricCard = memo(function MetricCard({ label, value, subtitle, icon, color, bgColor }: MetricCardProps) {
  return (
    <Box
      sx={{
        p: 2,
        borderRadius: 1,
        bgcolor: bgColor || 'action.hover',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
        <Box sx={{ color: color || 'text.secondary', mt: 0.5 }}>
          {icon}
        </Box>
        <Box sx={{ flex: 1 }}>
          <Typography variant="caption" color="text.secondary">
            {label}
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 600, color: color || 'text.primary' }}>
            {value}
          </Typography>
          {subtitle && (
            <Typography variant="caption" color="text.secondary">
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
    </Box>
  );
});

export const MetricsPanel = memo(function MetricsPanel({ open, onClose, topology, summary }: MetricsPanelProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  const healthyColors = getStatusColors(SERVER_HEALTH_COLORS.HEALTHY, isDarkMode);
  const degradedColors = getStatusColors(SERVER_HEALTH_COLORS.DEGRADED, isDarkMode);
  const unhealthyColors = getStatusColors(SERVER_HEALTH_COLORS.UNHEALTHY, isDarkMode);
  const latencyColors = getLatencyQualityColor(summary.avgLatencyMs, isDarkMode);

  // Calculate aggregate metrics from topology (single pass for efficiency)
  const aggregateMetrics = useMemo(() => {
    let count = 0;
    let totalCpu = 0;
    let totalMemory = 0;
    let totalJitter = 0;
    let totalErrorRate = 0;
    let maxLatency = 0;
    let minLatency = Infinity;

    for (const node of topology.nodes) {
      if (!node.metrics) continue;
      count++;
      totalCpu += node.metrics.cpuPercent;
      totalMemory += node.metrics.memoryPercent;
      totalJitter += node.metrics.jitterMs;
      totalErrorRate += node.metrics.errorRate;
      if (node.metrics.latencyMs > maxLatency) maxLatency = node.metrics.latencyMs;
      if (node.metrics.latencyMs < minLatency) minLatency = node.metrics.latencyMs;
    }

    if (count === 0) {
      return {
        avgCpu: 0,
        avgMemory: 0,
        avgJitter: 0,
        avgErrorRate: 0,
        maxLatency: 0,
        minLatency: 0,
      };
    }

    return {
      avgCpu: Math.round(totalCpu / count),
      avgMemory: Math.round(totalMemory / count),
      avgJitter: Math.round(totalJitter / count),
      avgErrorRate: Math.round((totalErrorRate / count) * 100) / 100,
      maxLatency,
      minLatency: minLatency === Infinity ? 0 : minLatency,
    };
  }, [topology.nodes]);

  // Session breakdown data for bar chart
  const sessionBreakdownData = useMemo(() => {
    const sb = summary.sessionBreakdown;
    const maxSession = Math.max(
      sb.inboundSessions,
      sb.outboundSessions,
      sb.ivrSessions,
      sb.queueSessions,
      sb.agentSessions,
      sb.onHoldSessions
    );

    return {
      data: [
        { label: 'Inbound', value: sb.inboundSessions, color: SESSION_BREAKDOWN_COLORS.INBOUND },
        { label: 'Outbound', value: sb.outboundSessions, color: SESSION_BREAKDOWN_COLORS.OUTBOUND },
        { label: 'IVR', value: sb.ivrSessions, color: SESSION_BREAKDOWN_COLORS.IVR },
        { label: 'Queue', value: sb.queueSessions, color: SESSION_BREAKDOWN_COLORS.QUEUE },
        { label: 'Agent', value: sb.agentSessions, color: SESSION_BREAKDOWN_COLORS.AGENT },
        { label: 'On Hold', value: sb.onHoldSessions, color: SESSION_BREAKDOWN_COLORS.ON_HOLD },
      ],
      maxValue: maxSession,
    };
  }, [summary.sessionBreakdown]);

  // Direction breakdown for pie-like display
  const directionData = useMemo(() => {
    const total = summary.sessionBreakdown.inboundSessions + summary.sessionBreakdown.outboundSessions;
    const inboundPercent = total > 0 ? (summary.sessionBreakdown.inboundSessions / total) * 100 : 50;
    return {
      inbound: summary.sessionBreakdown.inboundSessions,
      outbound: summary.sessionBreakdown.outboundSessions,
      inboundPercent: Math.round(inboundPercent),
      outboundPercent: Math.round(100 - inboundPercent),
    };
  }, [summary.sessionBreakdown]);

  // Datacenter health summary
  const dcHealthData = useMemo(() => {
    return summary.datacenterSummaries.map(dc => ({
      id: dc.id.toUpperCase(),
      healthy: dc.healthyNodes,
      degraded: dc.degradedNodes,
      unhealthy: dc.unhealthyNodes,
      utilization: Math.round(dc.utilizationPercent),
    }));
  }, [summary.datacenterSummaries]);

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: {
          width: 360,
          bgcolor: isDarkMode ? 'hsl(220, 20%, 13%)' : 'hsl(220, 20%, 98%)',
        },
      }}
    >
      {/* Header */}
      <Box
        sx={{
          p: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid',
          borderColor: isDarkMode ? 'hsl(220, 20%, 25%)' : 'hsl(220, 20%, 85%)',
        }}
      >
        <Typography variant="h6">System Metrics</Typography>
        <IconButton onClick={onClose} size="small" aria-label="Close metrics panel">
          <CloseIcon />
        </IconButton>
      </Box>

      {/* Content */}
      <Box sx={{ p: 2, overflow: 'auto' }}>
        {/* Capacity Overview */}
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          Capacity Overview
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5, mb: 3 }}>
          <MetricCard
            label="Total Sessions"
            value={summary.totalActiveSessions.toLocaleString()}
            subtitle={`of ${summary.totalCapacity.toLocaleString()} max`}
            icon={<CallIcon sx={{ fontSize: 20 }} />}
          />
          <MetricCard
            label="Utilization"
            value={`${Math.round(summary.utilizationPercent)}%`}
            subtitle={`${summary.headroomSessions.toLocaleString()} available`}
            icon={<SpeedIcon sx={{ fontSize: 20 }} />}
            color={summary.utilizationPercent > CAPACITY_THRESHOLDS.CRITICAL ? unhealthyColors.text :
                   summary.utilizationPercent > CAPACITY_THRESHOLDS.WARNING ? degradedColors.text : healthyColors.text}
            bgColor={summary.utilizationPercent > CAPACITY_THRESHOLDS.CRITICAL ? unhealthyColors.bg :
                     summary.utilizationPercent > CAPACITY_THRESHOLDS.WARNING ? degradedColors.bg : healthyColors.bg}
          />
        </Box>

        <Divider sx={{ my: 2 }} />

        {/* Session Direction */}
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          Session Direction
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, mb: 3 }}>
          <Box sx={{ flex: 1, textAlign: 'center', p: 1.5, borderRadius: 1, bgcolor: 'action.hover' }}>
            <CallReceivedIcon sx={{ color: SESSION_BREAKDOWN_COLORS.INBOUND, mb: 0.5 }} />
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              {directionData.inbound.toLocaleString()}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Inbound ({directionData.inboundPercent}%)
            </Typography>
          </Box>
          <Box sx={{ flex: 1, textAlign: 'center', p: 1.5, borderRadius: 1, bgcolor: 'action.hover' }}>
            <CallMadeIcon sx={{ color: SESSION_BREAKDOWN_COLORS.OUTBOUND, mb: 0.5 }} />
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              {directionData.outbound.toLocaleString()}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Outbound ({directionData.outboundPercent}%)
            </Typography>
          </Box>
        </Box>

        {/* Session Breakdown Bar Chart */}
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          Session Breakdown
        </Typography>
        <Box sx={{ mb: 3 }}>
          <HorizontalBarChart data={sessionBreakdownData.data} maxValue={sessionBreakdownData.maxValue} />
        </Box>

        <Divider sx={{ my: 2 }} />

        {/* Quality Metrics */}
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          Quality Metrics (Avg)
        </Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5, mb: 3 }}>
          <MetricCard
            label="Latency"
            value={`${summary.avgLatencyMs}ms`}
            subtitle={`${aggregateMetrics.minLatency}-${aggregateMetrics.maxLatency}ms range`}
            icon={<NetworkCheckIcon sx={{ fontSize: 20 }} />}
            color={latencyColors.text}
            bgColor={latencyColors.bg}
          />
          <MetricCard
            label="Jitter"
            value={`${aggregateMetrics.avgJitter}ms`}
            icon={<NetworkCheckIcon sx={{ fontSize: 20 }} />}
          />
          <MetricCard
            label="CPU"
            value={`${aggregateMetrics.avgCpu}%`}
            icon={<MemoryIcon sx={{ fontSize: 20 }} />}
            color={aggregateMetrics.avgCpu > CPU_THRESHOLDS.CRITICAL ? unhealthyColors.text :
                   aggregateMetrics.avgCpu > CPU_THRESHOLDS.WARNING ? degradedColors.text : undefined}
          />
          <MetricCard
            label="Memory"
            value={`${aggregateMetrics.avgMemory}%`}
            icon={<MemoryIcon sx={{ fontSize: 20 }} />}
            color={aggregateMetrics.avgMemory > MEMORY_THRESHOLDS.CRITICAL ? unhealthyColors.text :
                   aggregateMetrics.avgMemory > MEMORY_THRESHOLDS.WARNING ? degradedColors.text : undefined}
          />
        </Box>

        <Divider sx={{ my: 2 }} />

        {/* Datacenter Health */}
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          Datacenter Health
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {dcHealthData.map((dc) => (
            <Box
              key={dc.id}
              sx={{
                p: 1.5,
                borderRadius: 1,
                bgcolor: 'action.hover',
                border: '1px solid',
                borderColor: dc.unhealthy > 0 ? unhealthyColors.text :
                             dc.degraded > 0 ? degradedColors.text : healthyColors.text,
              }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                  {dc.id}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {dc.utilization}% utilized
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', gap: 1.5 }} role="list" aria-label="Node health counts">
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }} role="listitem">
                  <Box
                    component="span"
                    sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: healthyColors.text }}
                    aria-hidden="true"
                  />
                  <Typography variant="caption">
                    {dc.healthy}
                    <VisuallyHidden> healthy</VisuallyHidden>
                  </Typography>
                </Box>
                {dc.degraded > 0 && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }} role="listitem">
                    <Box
                      component="span"
                      sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: degradedColors.text }}
                      aria-hidden="true"
                    />
                    <Typography variant="caption">
                      {dc.degraded}
                      <VisuallyHidden> degraded</VisuallyHidden>
                    </Typography>
                  </Box>
                )}
                {dc.unhealthy > 0 && (
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }} role="listitem">
                    <Box
                      component="span"
                      sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: unhealthyColors.text }}
                      aria-hidden="true"
                    />
                    <Typography variant="caption">
                      {dc.unhealthy}
                      <VisuallyHidden> unhealthy</VisuallyHidden>
                    </Typography>
                  </Box>
                )}
              </Box>
            </Box>
          ))}
        </Box>
      </Box>
    </Drawer>
  );
});
