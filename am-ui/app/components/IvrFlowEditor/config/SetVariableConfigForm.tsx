'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function SetVariableConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="Variable Name" size="small" fullWidth {...field('variableName')} />
      <TextField label="Expression" size="small" fullWidth {...field('expression')} />
    </Stack>
  );
}
