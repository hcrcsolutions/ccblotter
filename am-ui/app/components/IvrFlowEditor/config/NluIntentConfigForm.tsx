'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function NluIntentConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="Input Variable" size="small" fullWidth {...field('inputVariable')} />
      <TextField label="Result Variable" size="small" fullWidth {...field('resultVariable')} />
      <TextField
        label="Confidence Threshold"
        size="small"
        fullWidth
        type="number"
        inputProps={{ step: 0.1 }}
        {...field('confidenceThreshold')}
      />
    </Stack>
  );
}
