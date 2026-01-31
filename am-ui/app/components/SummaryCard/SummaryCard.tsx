'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';

export interface SummaryCardProps {
  /** Card title displayed above the value */
  title: string;
  /** Main value to display - can be number, string, or formatted ReactNode */
  value: React.ReactNode;
  /** Icon to display in the colored box (should have fontSize: 32) */
  icon: React.ReactNode;
  /** Text/icon color (HSL format recommended) */
  color: string;
  /** Background color for icon box (HSL format recommended) */
  bgColor: string;
  /** Card width in pixels (default: 200) */
  width?: number;
  /** Optional border color for the card */
  borderColor?: string;
  /** Optional background color for the entire card */
  cardBgColor?: string;
}

/**
 * Summary card component for dashboard metrics.
 * Used across Agents, Calls, and Infrastructure pages.
 */
export function SummaryCard({ title, value, icon, color, bgColor, width = 200, borderColor, cardBgColor }: SummaryCardProps) {
  // Guard against problematic values
  const displayValue = value ?? '-';

  return (
    <Card
      sx={{
        width,
        ...(borderColor && {
          border: '2px solid',
          borderColor,
        }),
        ...(cardBgColor && {
          backgroundColor: cardBgColor,
        }),
      }}
    >
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                mb: 0.5,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {title}
            </Typography>
            <Typography
              variant="h3"
              component="div"
              sx={{
                fontWeight: 600,
                color,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {displayValue}
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
              flexShrink: 0,
              ml: 1,
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}
