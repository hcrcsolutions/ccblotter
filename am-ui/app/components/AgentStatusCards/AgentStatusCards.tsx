'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import CoffeeIcon from '@mui/icons-material/Coffee';
import DoNotDisturbIcon from '@mui/icons-material/DoNotDisturb';
import GroupIcon from '@mui/icons-material/Group';
import type { AgentSummary } from '../../types';

interface StatusCardProps {
  title: string;
  count: number;
  icon: React.ReactNode;
  color: string;
  bgColor: string;
}

function StatusCard({ title, count, icon, color, bgColor }: StatusCardProps) {
  return (
    <Card sx={{ width: 200 }}>
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
              {title}
            </Typography>
            <Typography variant="h3" component="div" sx={{ fontWeight: 600, color }}>
              {count}
            </Typography>
          </Box>
          <Box
            sx={{
              width: 56,
              height: 56,
              borderRadius: 2,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: bgColor,
              color,
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}

interface AgentStatusCardsProps {
  summary: AgentSummary;
}

export function AgentStatusCards({ summary }: AgentStatusCardsProps) {
  const cards = [
    {
      title: 'All Agents',
      count: summary.total,
      icon: <GroupIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(220, 20%, 40%)',
      bgColor: 'hsl(220, 20%, 95%)',
    },
    {
      title: 'Online',
      count: summary.online,
      icon: <CheckCircleIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(120, 59%, 30%)',
      bgColor: 'hsl(120, 80%, 95%)',
    },
    {
      title: 'On Call',
      count: summary.onCall,
      icon: <PhoneInTalkIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(210, 98%, 42%)',
      bgColor: 'hsl(210, 100%, 95%)',
    },
    {
      title: 'Away',
      count: summary.away,
      icon: <CoffeeIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(45, 90%, 40%)',
      bgColor: 'hsl(45, 100%, 95%)',
    },
    {
      title: 'Unavailable',
      count: summary.unavailable,
      icon: <DoNotDisturbIcon sx={{ fontSize: 32 }} />,
      color: 'hsl(0, 90%, 40%)',
      bgColor: 'hsl(0, 100%, 95%)',
    },
  ];

  return (
    <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
      {cards.map((card) => (
        <StatusCard key={card.title} {...card} />
      ))}
    </Box>
  );
}
