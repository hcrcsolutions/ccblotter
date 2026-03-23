'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import type { ScenarioStep } from '../../types';
import { ACTION_LABELS } from './scenarioConstants';

interface StepConfigFormProps {
  step: ScenarioStep;
  onUpdate: (updates: Partial<ScenarioStep>) => void;
}

export function StepConfigForm({ step, onUpdate }: StepConfigFormProps) {
  const updateConfig = (key: string, value: unknown) => {
    onUpdate({ config: { ...step.config, [key]: value } });
  };

  return (
    <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
        {ACTION_LABELS[step.action] || step.action} Configuration
      </Typography>

      <TextField
        label={step.action === 'WAIT' ? 'Wait duration (ms)' : 'Delay before step (ms)'}
        type="number"
        value={step.delayMs}
        onChange={(e) => onUpdate({ delayMs: Math.max(0, parseInt(e.target.value) || 0) })}
        fullWidth
        size="small"
      />

      {step.action === 'DIAL_IN' && (
        <TextField
          label="Flow ID"
          value={(step.config.flowId as string) || ''}
          onChange={(e) => updateConfig('flowId', e.target.value)}
          fullWidth
          size="small"
          placeholder="UUID of the IVR flow"
        />
      )}

      {step.action === 'IVR_INPUT' && (
        <TextField
          label="DTMF Input"
          value={(step.config.input as string) || ''}
          onChange={(e) => updateConfig('input', e.target.value)}
          fullWidth
          size="small"
          placeholder="e.g. 1, 2, #"
        />
      )}

      {step.action === 'WAIT_IN_QUEUE' && (
        <TextField
          label="Max Wait (ms)"
          type="number"
          value={(step.config.maxWaitMs as number) || ''}
          onChange={(e) => updateConfig('maxWaitMs', parseInt(e.target.value) || undefined)}
          fullWidth
          size="small"
        />
      )}

      {step.action === 'WHISPER' && (
        <TextField
          label="Whisper Message"
          value={(step.config.whisperMessage as string) || ''}
          onChange={(e) => updateConfig('whisperMessage', e.target.value)}
          fullWidth
          size="small"
          placeholder="Message the agent hears"
        />
      )}

      {step.action === 'HOLD_CALL' && (
        <TextField
          label="Hold Duration (ms)"
          type="number"
          value={(step.config.holdDurationMs as number) || ''}
          onChange={(e) => updateConfig('holdDurationMs', parseInt(e.target.value) || undefined)}
          fullWidth
          size="small"
        />
      )}

    </Box>
  );
}
