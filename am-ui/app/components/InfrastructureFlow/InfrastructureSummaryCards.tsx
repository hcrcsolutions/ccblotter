'use client';

import { memo, useMemo } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import CableIcon from '@mui/icons-material/Cable';
import SecurityIcon from '@mui/icons-material/Security';
import DnsIcon from '@mui/icons-material/Dns';
import VideocamIcon from '@mui/icons-material/Videocam';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import SpeedIcon from '@mui/icons-material/Speed';
import NetworkCheckIcon from '@mui/icons-material/NetworkCheck';
import { useThemeContext } from '../../context/ThemeContext';
import { SummaryCard } from '../SummaryCard/SummaryCard';
import { SERVER_HEALTH_COLORS, SERVER_TYPE_COLORS, getStatusColors, getLatencyQualityColor, CAPACITY_THRESHOLDS } from '../../lib/statusColors';
import type { InfrastructureSummary } from '../../types';

interface InfrastructureSummaryCardsProps {
  summary: InfrastructureSummary;
}

// Compact infrastructure type item for the combined card
interface InfraTypeItemProps {
  icon: React.ReactNode;
  label: string;
  count: number;
  color: string;
  bgColor: string;
}

const InfraTypeItem = memo(function InfraTypeItem({ icon, label, count, color, bgColor }: InfraTypeItemProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Box
        sx={{
          width: 28,
          height: 28,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: bgColor,
          color,
          flexShrink: 0,
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h6" sx={{ fontWeight: 600, color, lineHeight: 1.2 }}>
          {count}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1 }}>
          {label}
        </Typography>
      </Box>
    </Box>
  );
});

// Health item for the node health card
interface HealthItemProps {
  count: number;
  color: string;
  label: string;
}

const HealthItem = memo(function HealthItem({ count, color, label }: HealthItemProps) {
  if (count === 0) return null;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
      <Box
        sx={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          backgroundColor: color,
          flexShrink: 0,
        }}
      />
      <Typography variant="caption" sx={{ color, fontWeight: 600 }}>
        {count}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
});

export const InfrastructureSummaryCards = memo(function InfrastructureSummaryCards({ summary }: InfrastructureSummaryCardsProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  // Get health status colors that match node backgrounds
  const healthyColors = getStatusColors(SERVER_HEALTH_COLORS.HEALTHY, isDarkMode);
  const degradedColors = getStatusColors(SERVER_HEALTH_COLORS.DEGRADED, isDarkMode);
  const unhealthyColors = getStatusColors(SERVER_HEALTH_COLORS.UNHEALTHY, isDarkMode);

  // Get server type colors
  const trunkColors = getStatusColors(SERVER_TYPE_COLORS.TRUNK, isDarkMode);
  const sbcColors = getStatusColors(SERVER_TYPE_COLORS.SBC, isDarkMode);
  const sipColors = getStatusColors(SERVER_TYPE_COLORS.SIP, isDarkMode);
  const mediaColors = getStatusColors(SERVER_TYPE_COLORS.MEDIA, isDarkMode);

  // Get latency quality color
  const latencyColors = getLatencyQualityColor(summary.avgLatencyMs, isDarkMode);

  // Format utilization
  const utilizationPercent = Math.round(summary.utilizationPercent);
  const utilizationColor = utilizationPercent > CAPACITY_THRESHOLDS.CRITICAL
    ? unhealthyColors
    : utilizationPercent > CAPACITY_THRESHOLDS.WARNING
      ? degradedColors
      : healthyColors;

  // Standard border color for cards
  const cardBorderColor = isDarkMode ? 'hsl(220, 20%, 30%)' : 'hsl(220, 20%, 85%)';

  const metricCards = useMemo(() => [
    // Sessions and capacity
    {
      title: 'Active Sessions',
      value: summary.totalActiveSessions.toLocaleString(),
      icon: <PhoneInTalkIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(200, 70%, 40%)',
      bgColor: 'hsl(200, 80%, 95%)',
      borderColor: cardBorderColor,
      width: 240,
    },
    {
      title: 'Capacity',
      value: `${utilizationPercent}%`,
      icon: <SpeedIcon sx={{ fontSize: 32 }} />,
      color: utilizationColor.text,
      bgColor: utilizationColor.bg,
      cardBgColor: utilizationColor.bg,
      borderColor: cardBorderColor,
    },
    // Quality metrics
    {
      title: 'Avg Latency',
      value: `${summary.avgLatencyMs}ms`,
      icon: <NetworkCheckIcon sx={{ fontSize: 32 }} />,
      color: latencyColors.text,
      bgColor: latencyColors.bg,
      cardBgColor: latencyColors.bg,
      borderColor: cardBorderColor,
    },
  ], [summary.totalActiveSessions, utilizationPercent, utilizationColor, summary.avgLatencyMs, latencyColors, cardBorderColor]);

  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'stretch' }}>
      {/* Combined Infrastructure Card - 2x2 grid */}
      <Card
        sx={{
          width: 240,
          border: '2px solid',
          borderColor: cardBorderColor,
        }}
      >
        <CardContent sx={{ py: 1.5, px: 2, '&:last-child': { pb: 1.5 } }}>
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: 1.5,
            }}
          >
            <InfraTypeItem
              icon={<CableIcon sx={{ fontSize: 16 }} />}
              label="Trunks"
              count={summary.trunkCount}
              color={trunkColors.text}
              bgColor={trunkColors.bg}
            />
            <InfraTypeItem
              icon={<SecurityIcon sx={{ fontSize: 16 }} />}
              label="SBCs"
              count={summary.sbcCount}
              color={sbcColors.text}
              bgColor={sbcColors.bg}
            />
            <InfraTypeItem
              icon={<DnsIcon sx={{ fontSize: 16 }} />}
              label="SIP"
              count={summary.sipServerCount}
              color={sipColors.text}
              bgColor={sipColors.bg}
            />
            <InfraTypeItem
              icon={<VideocamIcon sx={{ fontSize: 16 }} />}
              label="Media"
              count={summary.mediaServerCount}
              color={mediaColors.text}
              bgColor={mediaColors.bg}
            />
          </Box>
        </CardContent>
      </Card>

      {/* Node Health Card - SIP + Media only */}
      <Card
        sx={{
          width: 160,
          border: '2px solid',
          borderColor: cardBorderColor,
        }}
      >
        <CardContent sx={{ py: 1.5, px: 2, '&:last-child': { pb: 1.5 } }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Node Health
          </Typography>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            <HealthItem
              count={summary.nodeHealthyCount}
              color={healthyColors.text}
              label="healthy"
            />
            <HealthItem
              count={summary.nodeDegradedCount}
              color={degradedColors.text}
              label="degraded"
            />
            <HealthItem
              count={summary.nodeUnhealthyCount}
              color={unhealthyColors.text}
              label="unhealthy"
            />
          </Box>
        </CardContent>
      </Card>

      {/* Metric cards */}
      {metricCards.map((card) => (
        <SummaryCard key={card.title} {...card} />
      ))}
    </Box>
  );
});
