'use client';

import { memo, useMemo } from 'react';
import Box from '@mui/material/Box';
import { useThemeContext } from '../../context/ThemeContext';
import { getTrendColor } from '../../lib/statusColors';
import type { TrendDataPoint } from '../../types';

interface SparklineProps {
  data: TrendDataPoint[];
  width?: number;
  height?: number;
  valueKey?: 'activeSessions' | 'cpuPercent';
  showTrendColor?: boolean;
}

/**
 * Mini sparkline chart component for trend visualization.
 * Displays a simple line chart without axes for compact inline use.
 */
export const Sparkline = memo(function Sparkline({
  data,
  width = 80,
  height = 24,
  valueKey = 'activeSessions',
  showTrendColor = true,
}: SparklineProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  const { path, trend, min, max } = useMemo(() => {
    if (!data || data.length < 2) {
      return { path: '', trend: 'flat' as const, min: 0, max: 0 };
    }

    const values = data.map(d => d[valueKey]);
    const minVal = Math.min(...values);
    const maxVal = Math.max(...values);
    const range = maxVal - minVal || 1;

    // Calculate path points
    const points = values.map((v, i) => {
      const x = (i / (values.length - 1)) * width;
      const y = height - ((v - minVal) / range) * (height - 4) - 2; // 2px padding
      return `${x},${y}`;
    });

    // Determine trend (comparing first and last thirds)
    const thirdSize = Math.max(1, Math.floor(values.length / 3));
    const firstThird = values.slice(0, thirdSize);
    const lastThird = values.slice(-thirdSize);
    const firstAvg = firstThird.reduce((a, b) => a + b, 0) / firstThird.length;
    const lastAvg = lastThird.reduce((a, b) => a + b, 0) / lastThird.length;

    let trendDir: 'up' | 'down' | 'flat' = 'flat';
    const threshold = range * 0.1; // 10% of range
    if (lastAvg > firstAvg + threshold) {
      trendDir = 'up';
    } else if (lastAvg < firstAvg - threshold) {
      trendDir = 'down';
    }

    return {
      path: `M ${points.join(' L ')}`,
      trend: trendDir,
      min: minVal,
      max: maxVal,
    };
  }, [data, width, height, valueKey]);

  if (!data || data.length < 2) {
    return (
      <Box
        sx={{
          width,
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Box
          sx={{
            width: '100%',
            height: 1,
            bgcolor: isDarkMode ? 'rgba(255,255,255,0.2)' : 'rgba(0,0,0,0.2)',
          }}
        />
      </Box>
    );
  }

  // Determine stroke color based on trend
  const strokeColor = showTrendColor
    ? getTrendColor(trend, isDarkMode)
    : getTrendColor('flat', isDarkMode);

  return (
    <Box
      sx={{
        width,
        height,
        position: 'relative',
      }}
    >
      <svg
        width={width}
        height={height}
        style={{ display: 'block' }}
      >
        <path
          d={path}
          fill="none"
          stroke={strokeColor}
          strokeWidth={1.5}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {/* End point dot */}
        {data.length > 0 && (
          <circle
            cx={width}
            cy={height - ((data[data.length - 1][valueKey] - min) / (max - min || 1)) * (height - 4) - 2}
            r={2}
            fill={strokeColor}
          />
        )}
      </svg>
    </Box>
  );
});
