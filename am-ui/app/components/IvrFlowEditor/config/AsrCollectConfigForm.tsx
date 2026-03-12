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

export function AsrCollectConfigForm({ config, onChange }: Props) {
  const field = (name: string) => ({
    value: (config[name] as string) ?? '',
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onChange({ ...config, [name]: e.target.value }),
  });

  return (
    <Stack spacing={2}>
      <TextField label="Prompt" size="small" fullWidth multiline minRows={2} {...field('prompt')} />
      <TextField label="Language" size="small" fullWidth {...field('language')} />
      <TextField
        label="Timeout (seconds)"
        size="small"
        fullWidth
        type="number"
        {...field('timeoutSeconds')}
      />
      <TextField label="Result Variable" size="small" fullWidth {...field('resultVariable')} />
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
