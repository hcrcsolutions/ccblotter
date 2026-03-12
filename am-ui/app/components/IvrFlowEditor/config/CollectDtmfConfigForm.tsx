'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function CollectDtmfConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="Prompt" size="small" fullWidth {...field('prompt')} />
      <TextField label="Max Digits" size="small" fullWidth type="number" {...field('maxDigits')} />
      <TextField label="Terminating Key" size="small" fullWidth {...field('terminatingKey')} />
      <TextField
        label="Timeout (seconds)"
        size="small"
        fullWidth
        type="number"
        {...field('timeoutSeconds')}
      />
      <TextField
        label="Inter-Digit Timeout (seconds)"
        size="small"
        fullWidth
        type="number"
        {...field('interDigitTimeoutSeconds')}
      />
      <TextField label="Result Variable" size="small" fullWidth {...field('resultVariable')} />
    </Stack>
  );
}
