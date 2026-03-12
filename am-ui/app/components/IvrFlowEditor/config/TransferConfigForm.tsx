'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function TransferConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="Destination" size="small" fullWidth {...field('destination')} />
      <TextField label="Transfer Type" size="small" fullWidth select {...field('transferType')}>
        <MenuItem value="AGENT">Agent</MenuItem>
        <MenuItem value="QUEUE">Queue</MenuItem>
        <MenuItem value="EXTERNAL">External</MenuItem>
      </TextField>
      <TextField label="Whisper Prompt" size="small" fullWidth {...field('whisperPrompt')} />
    </Stack>
  );
}
