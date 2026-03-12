'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function PlayPromptConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="Audio URL" size="small" fullWidth {...field('audioUrl')} />
      <TextField
        label="TTS Text"
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
        {...field('ttsText')}
      />
      <FormControlLabel
        control={
          <Switch
            checked={Boolean(config.bargeIn)}
            onChange={(e) => onChange({ ...config, bargeIn: e.target.checked })}
            size="small"
          />
        }
        label="Barge-In"
      />
    </Stack>
  );
}
