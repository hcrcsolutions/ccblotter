'use client';

import { memo } from 'react';
import Box from '@mui/material/Box';
import { visuallyHidden } from '@mui/utils';

interface VisuallyHiddenProps {
  children: React.ReactNode;
}

/**
 * Visually hides content while keeping it accessible to screen readers.
 * Use for providing context that is visually implied but not explicit.
 */
export const VisuallyHidden = memo(function VisuallyHidden({ children }: VisuallyHiddenProps) {
  return (
    <Box component="span" sx={visuallyHidden}>
      {children}
    </Box>
  );
});
