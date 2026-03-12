'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function MenuConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField
        label="Prompt"
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
        {...field('prompt')}
      />
      <TextField
        label="Valid Options"
        size="small"
        fullWidth
        helperText="Comma-separated keys, e.g. 1,2,3,0"
        value={Array.isArray(config.validOptions) ? (config.validOptions as string[]).join(',') : (config.validOptions as string) ?? ''}
        onChange={(e) => {
          const raw = e.target.value;
          const options = raw ? raw.split(',').map((s) => s.trim()).filter(Boolean) : [];
          onChange({ ...config, validOptions: options });
        }}
      />
      <TextField
        label="Timeout (seconds)"
        size="small"
        fullWidth
        type="number"
        {...field('timeoutSeconds')}
      />
      <TextField
        label="Max Retries"
        size="small"
        fullWidth
        type="number"
        {...field('maxRetries')}
      />
      <TextField
        label="Invalid Prompt"
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
        {...field('invalidPrompt')}
      />
      <TextField
        label="Timeout Prompt"
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
        {...field('timeoutPrompt')}
      />
      <TextField label="Result Variable" size="small" fullWidth {...field('resultVariable')} />
    </Stack>
  );
}
