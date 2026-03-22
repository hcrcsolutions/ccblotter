'use client';

import * as React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import type { ScenarioActor, ScenarioStep, ScenarioAction } from '../../types';
import { ACTIONS_BY_ROLE, ACTION_LABELS, ROLE_LABELS } from './scenarioConstants';

interface AddStepDialogProps {
  open: boolean;
  actors: ScenarioActor[];
  onClose: () => void;
  onAdd: (step: ScenarioStep) => void;
}

export function AddStepDialog({ open, actors, onClose, onAdd }: AddStepDialogProps) {
  const [actorId, setActorId] = React.useState('');
  const [action, setAction] = React.useState<ScenarioAction | ''>('');

  const selectedActor = actors.find((a) => a.id === actorId) || null;

  React.useEffect(() => {
    if (open) {
      setActorId(actors.length > 0 ? actors[0].id : '');
      setAction('');
    }
  }, [open, actors]);

  React.useEffect(() => {
    if (actorId) {
      setAction('');
    }
  }, [actorId]);

  if (actors.length === 0) {
    return (
      <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
        <DialogTitle>Add Step</DialogTitle>
        <DialogContent>
          No actors defined. Add an actor first.
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }

  const availableActions = selectedActor ? (ACTIONS_BY_ROLE[selectedActor.role] || []) : [];

  const handleAdd = () => {
    if (!action || !actorId) {
      return;
    }
    onAdd({
      id: `step-${Date.now()}`,
      actorId,
      action,
      delayMs: action === 'WAIT' ? 1000 : 0,
      config: {},
    });
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Add Step</DialogTitle>
      <DialogContent>
        <FormControl fullWidth sx={{ mt: 1, mb: 2 }}>
          <InputLabel>Actor</InputLabel>
          <Select
            value={actorId}
            label="Actor"
            onChange={(e) => setActorId(e.target.value)}
          >
            {actors.map((a) => (
              <MenuItem key={a.id} value={a.id}>
                {a.name} ({ROLE_LABELS[a.role]})
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl fullWidth sx={{ mb: 2 }}>
          <InputLabel>Action</InputLabel>
          <Select
            value={action}
            label="Action"
            onChange={(e) => setAction(e.target.value as ScenarioAction)}
            disabled={!actorId}
          >
            {availableActions.map((a) => (
              <MenuItem key={a} value={a}>{ACTION_LABELS[a] || a}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button onClick={handleAdd} variant="contained" disabled={!action || !actorId}>
          Add Step
        </Button>
      </DialogActions>
    </Dialog>
  );
}
