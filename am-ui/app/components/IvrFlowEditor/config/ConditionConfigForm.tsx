'use client';

import * as React from 'react';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
}

export function ConditionConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="Variable Name" size="small" fullWidth {...field('variableName')} />
      <TextField label="Operator" size="small" fullWidth select {...field('operator')}>
        <MenuItem value="EQ">EQ</MenuItem>
        <MenuItem value="NEQ">NEQ</MenuItem>
        <MenuItem value="GT">GT</MenuItem>
        <MenuItem value="LT">LT</MenuItem>
        <MenuItem value="GTE">GTE</MenuItem>
        <MenuItem value="LTE">LTE</MenuItem>
        <MenuItem value="CONTAINS">CONTAINS</MenuItem>
        <MenuItem value="MATCHES">MATCHES</MenuItem>
      </TextField>
      <TextField label="Value" size="small" fullWidth {...field('value')} />
    </Stack>
  );
}
