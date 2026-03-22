'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import type { ScenarioActor, ActorRole } from '../../types';
import { ROLE_LABELS, CALLER_PROFILES, AGENT_PROFILES } from './scenarioConstants';

interface ActorConfigFormProps {
  actor: ScenarioActor;
  onUpdate: (updates: Partial<ScenarioActor>) => void;
}

export function ActorConfigForm({ actor, onUpdate }: ActorConfigFormProps) {
  const profileOptions = actor.role === 'CALLER' ? CALLER_PROFILES
    : actor.role === 'AGENT' ? AGENT_PROFILES
    : [];

  return (
    <Box sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
        Actor Configuration
      </Typography>

      <TextField
        label="Name"
        value={actor.name}
        onChange={(e) => onUpdate({ name: e.target.value })}
        fullWidth
        size="small"
      />

      <FormControl fullWidth size="small">
        <InputLabel>Role</InputLabel>
        <Select
          value={actor.role}
          label="Role"
          onChange={(e) => onUpdate({ role: e.target.value as ActorRole, profile: undefined, config: {} })}
        >
          {(Object.keys(ROLE_LABELS) as ActorRole[]).map((r) => (
            <MenuItem key={r} value={r}>{ROLE_LABELS[r]}</MenuItem>
          ))}
        </Select>
      </FormControl>

      {profileOptions.length > 0 && (
        <FormControl fullWidth size="small">
          <InputLabel>Profile</InputLabel>
          <Select
            value={actor.profile || ''}
            label="Profile"
            onChange={(e) => onUpdate({ profile: e.target.value || undefined })}
          >
            <MenuItem value=""><em>None</em></MenuItem>
            {profileOptions.map((p) => (
              <MenuItem key={p.value} value={p.value}>{p.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
      )}

      {actor.role === 'CALLER' && (
        <>
          <TextField
            label="Phone Number"
            value={(actor.config.phoneNumber as string) || ''}
            onChange={(e) => onUpdate({ config: { ...actor.config, phoneNumber: e.target.value } })}
            fullWidth
            size="small"
            placeholder="+16505551234"
          />
        </>
      )}

      {actor.role === 'AGENT' && (
        <TextField
          label="Agent Name"
          value={(actor.config.agentName as string) || ''}
          onChange={(e) => onUpdate({ config: { ...actor.config, agentName: e.target.value } })}
          fullWidth
          size="small"
        />
      )}
    </Box>
  );
}
