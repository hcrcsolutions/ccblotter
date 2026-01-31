'use client';

import { memo } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import LocationOnIcon from '@mui/icons-material/LocationOn';
import { useThemeContext } from '../../context/ThemeContext';
import { SERVER_HEALTH_COLORS, getStatusColors } from '../../lib/statusColors';

interface DatacenterHeaderData {
  id: string;
  region: string;
  healthyNodes: number;
  degradedNodes: number;
  unhealthyNodes: number;
  width: number;
}

interface DatacenterHeaderProps {
  data: DatacenterHeaderData;
}

function DatacenterHeaderComponent({ data }: DatacenterHeaderProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';

  const healthyColors = getStatusColors(SERVER_HEALTH_COLORS.HEALTHY, isDarkMode);
  const degradedColors = getStatusColors(SERVER_HEALTH_COLORS.DEGRADED, isDarkMode);
  const unhealthyColors = getStatusColors(SERVER_HEALTH_COLORS.UNHEALTHY, isDarkMode);

  // DC accent color (blue)
  const dcAccentColor = isDarkMode ? 'hsl(210, 70%, 55%)' : 'hsl(210, 80%, 45%)';

  return (
    <Box
      sx={{
        width: data.width,
        height: 36,
        display: 'flex',
        alignItems: 'center',
        px: 2,
        borderRadius: 1,
        bgcolor: isDarkMode ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
        borderBottom: '2px solid',
        borderColor: dcAccentColor,
      }}
    >
      {/* DC name and region */}
      <LocationOnIcon sx={{ fontSize: 18, color: dcAccentColor, mr: 1 }} />
      <Typography
        variant="subtitle2"
        sx={{
          fontWeight: 600,
          color: isDarkMode ? 'grey.200' : 'grey.800',
          textTransform: 'uppercase',
          letterSpacing: 0.5,
        }}
      >
        {data.id}
      </Typography>
      <Typography
        variant="caption"
        sx={{
          color: isDarkMode ? 'grey.500' : 'grey.600',
          ml: 1,
        }}
      >
        {data.region}
      </Typography>

      {/* Health counts */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, ml: 2 }}>
        {data.healthyNodes > 0 && (
          <Chip
            icon={<CheckCircleIcon sx={{ fontSize: 14 }} />}
            label={data.healthyNodes}
            size="small"
            sx={{
              height: 22,
              fontSize: '0.7rem',
              bgcolor: healthyColors.bg,
              color: healthyColors.text,
              '& .MuiChip-icon': { color: healthyColors.text },
            }}
          />
        )}
        {data.degradedNodes > 0 && (
          <Chip
            icon={<WarningIcon sx={{ fontSize: 14 }} />}
            label={data.degradedNodes}
            size="small"
            sx={{
              height: 22,
              fontSize: '0.7rem',
              bgcolor: degradedColors.bg,
              color: degradedColors.text,
              '& .MuiChip-icon': { color: degradedColors.text },
            }}
          />
        )}
        {data.unhealthyNodes > 0 && (
          <Chip
            icon={<ErrorIcon sx={{ fontSize: 14 }} />}
            label={data.unhealthyNodes}
            size="small"
            sx={{
              height: 22,
              fontSize: '0.7rem',
              bgcolor: unhealthyColors.bg,
              color: unhealthyColors.text,
              '& .MuiChip-icon': { color: unhealthyColors.text },
            }}
          />
        )}
      </Box>
    </Box>
  );
}

export const DatacenterHeader = memo(DatacenterHeaderComponent);
