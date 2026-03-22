'use client';

import * as React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import type { ActorRole, CallerProfile, AgentProfile, ScenarioActor } from '../../types';
import { ROLE_LABELS, CALLER_PROFILES, AGENT_PROFILES } from './scenarioConstants';

interface AddActorDialogProps {
  open: boolean;
  onClose: () => void;
  onAdd: (actor: ScenarioActor) => void;
}

export function AddActorDialog({ open, onClose, onAdd }: AddActorDialogProps) {
  const [name, setName] = React.useState('');
  const [role, setRole] = React.useState<ActorRole>('CALLER');
  const [profile, setProfile] = React.useState('');

  const handleAdd = () => {
    if (!name.trim()) {
      return;
    }
    onAdd({
      id: `actor-${Date.now()}`,
      name: name.trim(),
      role,
      profile: (profile || undefined) as CallerProfile | AgentProfile | undefined,
      config: {},
    });
    setName('');
    setRole('CALLER');
    setProfile('');
    onClose();
  };

  const profileOptions = role === 'CALLER' ? CALLER_PROFILES
    : role === 'AGENT' ? AGENT_PROFILES
    : [];

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Add Actor</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          margin="dense"
          label="Actor Name"
          fullWidth
          value={name}
          onChange={(e) => setName(e.target.value)}
          sx={{ mb: 2 }}
        />
        <FormControl fullWidth sx={{ mb: 2 }}>
          <InputLabel>Role</InputLabel>
          <Select
            value={role}
            label="Role"
            onChange={(e) => { setRole(e.target.value as ActorRole); setProfile(''); }}
          >
            {(Object.keys(ROLE_LABELS) as ActorRole[]).map((r) => (
              <MenuItem key={r} value={r}>{ROLE_LABELS[r]}</MenuItem>
            ))}
          </Select>
        </FormControl>
        {profileOptions.length > 0 && (
          <FormControl fullWidth>
            <InputLabel>Profile (optional)</InputLabel>
            <Select
              value={profile}
              label="Profile (optional)"
              onChange={(e) => setProfile(e.target.value)}
            >
              <MenuItem value=""><em>None</em></MenuItem>
              {profileOptions.map((p) => (
                <MenuItem key={p.value} value={p.value}>
                  {p.label} — {p.description}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button onClick={handleAdd} variant="contained" disabled={!name.trim()}>
          Add
        </Button>
      </DialogActions>
    </Dialog>
  );
}
