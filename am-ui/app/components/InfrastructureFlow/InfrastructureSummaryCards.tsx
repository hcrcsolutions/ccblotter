'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import DnsIcon from '@mui/icons-material/Dns';
import VideocamIcon from '@mui/icons-material/Videocam';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import { SummaryCard } from '../SummaryCard/SummaryCard';
import type { InfrastructureSummary } from '../../types';

interface InfrastructureSummaryCardsProps {
  summary: InfrastructureSummary;
}

export function InfrastructureSummaryCards({ summary }: InfrastructureSummaryCardsProps) {
  const cards = [
    {
      title: 'SIP Servers',
      value: summary.sipServerCount,
      icon: <DnsIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(210, 98%, 42%)',
      bgColor: 'hsl(210, 100%, 95%)',
    },
    {
      title: 'Media Servers',
      value: summary.mediaServerCount,
      icon: <VideocamIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(280, 60%, 45%)',
      bgColor: 'hsl(280, 80%, 95%)',
    },
    {
      title: 'Active Sessions',
      value: summary.totalActiveSessions,
      icon: <PhoneInTalkIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(200, 70%, 40%)',
      bgColor: 'hsl(200, 80%, 95%)',
    },
    {
      title: 'Healthy',
      value: summary.healthyCount,
      icon: <CheckCircleIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(120, 59%, 30%)',
      bgColor: 'hsl(120, 80%, 95%)',
    },
  ];

  // Only show degraded/unhealthy if there are any
  if (summary.degradedCount > 0) {
    cards.push({
      title: 'Degraded',
      value: summary.degradedCount,
      icon: <WarningIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(45, 90%, 40%)',
      bgColor: 'hsl(45, 100%, 95%)',
    });
  }

  if (summary.unhealthyCount > 0) {
    cards.push({
      title: 'Unhealthy',
      value: summary.unhealthyCount,
      icon: <ErrorIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(0, 90%, 40%)',
      bgColor: 'hsl(0, 100%, 95%)',
    });
  }

  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
      {cards.map((card) => (
        <SummaryCard key={card.title} {...card} />
      ))}
    </Box>
  );
}
