'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Card from '@mui/material/Card';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import type { ScenarioAssertion, AssertionType } from '../../types';
import { ASSERTION_TYPE_LABELS } from './scenarioConstants';

interface AssertionPanelProps {
  assertions: ScenarioAssertion[];
  stepId: string;
  onAdd: (assertion: ScenarioAssertion) => void;
  onUpdate: (id: string, updates: Partial<ScenarioAssertion>) => void;
  onRemove: (id: string) => void;
}

export function AssertionPanel({ assertions, stepId, onAdd, onUpdate, onRemove }: AssertionPanelProps) {
  const stepAssertions = assertions.filter((a) => a.afterStepId === stepId);
  const [adding, setAdding] = React.useState(false);
  const [newType, setNewType] = React.useState<AssertionType>('AGENT_IN_STATE');

  const handleAdd = () => {
    onAdd({
      id: `assert-${Date.now()}`,
      type: newType,
      afterStepId: stepId,
      config: {},
    });
    setAdding(false);
  };

  return (
    <Box sx={{ p: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
          Assertions ({stepAssertions.length})
        </Typography>
        <Button size="small" startIcon={<AddIcon />} onClick={() => setAdding(true)}>
          Add
        </Button>
      </Box>

      {stepAssertions.map((assertion) => (
        <Card key={assertion.id} variant="outlined" sx={{ p: 1, mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2" sx={{ flex: 1 }}>
              {ASSERTION_TYPE_LABELS[assertion.type]}
            </Typography>
            <IconButton size="small" onClick={() => onRemove(assertion.id)}>
              <DeleteIcon sx={{ fontSize: 16 }} />
            </IconButton>
          </Box>

          {assertion.type === 'AGENT_IN_STATE' && (
            <TextField
              label="Expected State"
              value={(assertion.config.expectedState as string) || ''}
              onChange={(e) => onUpdate(assertion.id, {
                config: { ...assertion.config, expectedState: e.target.value },
              })}
              fullWidth
              size="small"
              sx={{ mt: 1 }}
              placeholder="ONLINE, ON_CALL, etc."
            />
          )}

          {assertion.type === 'QUEUE_LENGTH' && (
            <TextField
              label="Expected Length"
              type="number"
              value={(assertion.config.expectedLength as number) ?? ''}
              onChange={(e) => onUpdate(assertion.id, {
                config: { ...assertion.config, expectedLength: parseInt(e.target.value) || 0 },
              })}
              fullWidth
              size="small"
              sx={{ mt: 1 }}
            />
          )}

          {assertion.type === 'CALL_CONNECTED_WITHIN' && (
            <TextField
              label="Max Duration (ms)"
              type="number"
              value={(assertion.config.maxDurationMs as number) ?? ''}
              onChange={(e) => onUpdate(assertion.id, {
                config: { ...assertion.config, maxDurationMs: parseInt(e.target.value) || 0 },
              })}
              fullWidth
              size="small"
              sx={{ mt: 1 }}
            />
          )}
        </Card>
      ))}

      {adding && (
        <Card variant="outlined" sx={{ p: 1 }}>
          <FormControl fullWidth size="small" sx={{ mb: 1 }}>
            <InputLabel>Assertion Type</InputLabel>
            <Select
              value={newType}
              label="Assertion Type"
              onChange={(e) => setNewType(e.target.value as AssertionType)}
            >
              {(Object.keys(ASSERTION_TYPE_LABELS) as AssertionType[]).map((t) => (
                <MenuItem key={t} value={t}>{ASSERTION_TYPE_LABELS[t]}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
            <Button size="small" onClick={() => setAdding(false)}>Cancel</Button>
            <Button size="small" variant="contained" onClick={handleAdd}>Add</Button>
          </Box>
        </Card>
      )}
    </Box>
  );
}
