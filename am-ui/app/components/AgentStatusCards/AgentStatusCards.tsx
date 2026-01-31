'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import CoffeeIcon from '@mui/icons-material/Coffee';
import DoNotDisturbIcon from '@mui/icons-material/DoNotDisturb';
import GroupIcon from '@mui/icons-material/Group';
import { SummaryCard } from '../SummaryCard/SummaryCard';
import type { AgentSummary } from '../../types';

interface AgentStatusCardsProps {
  summary: AgentSummary;
}

export function AgentStatusCards({ summary }: AgentStatusCardsProps) {
  const cards = [
    {
      title: 'All Agents',
      value: summary.total,
      icon: <GroupIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(220, 20%, 40%)',
      bgColor: 'hsl(220, 20%, 95%)',
    },
    {
      title: 'Online',
      value: summary.online,
      icon: <CheckCircleIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(120, 59%, 30%)',
      bgColor: 'hsl(120, 80%, 95%)',
    },
    {
      title: 'On Call',
      value: summary.onCall,
      icon: <PhoneInTalkIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(210, 98%, 42%)',
      bgColor: 'hsl(210, 100%, 95%)',
    },
    {
      title: 'Away',
      value: summary.away,
      icon: <CoffeeIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(45, 90%, 40%)',
      bgColor: 'hsl(45, 100%, 95%)',
    },
    {
      title: 'Unavailable',
      value: summary.unavailable,
      icon: <DoNotDisturbIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(0, 90%, 40%)',
      bgColor: 'hsl(0, 100%, 95%)',
    },
  ];

  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
      {cards.map((card) => (
        <SummaryCard key={card.title} {...card} />
      ))}
    </Box>
  );
}
