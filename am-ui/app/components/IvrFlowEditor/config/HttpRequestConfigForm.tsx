'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function HttpRequestConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="URL" size="small" fullWidth {...field('url')} />
      <TextField label="Method" size="small" fullWidth select {...field('method')}>
        <MenuItem value="GET">GET</MenuItem>
        <MenuItem value="POST">POST</MenuItem>
        <MenuItem value="PUT">PUT</MenuItem>
        <MenuItem value="DELETE">DELETE</MenuItem>
      </TextField>
      <TextField
        label="Body Template"
        size="small"
        fullWidth
        multiline
        InputLabelProps={{ shrink: true }}
        sx={{
          '& .MuiInputBase-root': {
            minHeight: '100px',
            alignItems: 'flex-start',
          },
          '& textarea': { fontFamily: 'monospace', fontSize: '0.875rem' },
        }}
        {...field('bodyTemplate')}
      />
      <TextField
        label="Response Variable"
        size="small"
        fullWidth
        {...field('responseVariable')}
      />
      <TextField
        label="Timeout (seconds)"
        size="small"
        fullWidth
        type="number"
        {...field('timeoutSeconds')}
      />
    </Stack>
  );
}
