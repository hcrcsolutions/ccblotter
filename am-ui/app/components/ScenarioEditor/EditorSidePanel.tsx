'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import DeleteIcon from '@mui/icons-material/Delete';
import type { ScenarioContent, ScenarioActor, ScenarioStep, ScenarioAssertion } from '../../types';
import { ACTION_LABELS } from './scenarioConstants';
import { ActorConfigForm } from './ActorConfigForm';
import { StepConfigForm } from './StepConfigForm';
import { AssertionPanel } from './AssertionPanel';

interface EditorSidePanelProps {
  content: ScenarioContent;
  selectedActorId: string | null;
  selectedStepId: string | null;
  onUpdateActor: (actorId: string, updates: Partial<ScenarioActor>) => void;
  onUpdateStep: (stepId: string, updates: Partial<ScenarioStep>) => void;
  onRemoveStep: (stepId: string) => void;
  onAddAssertion: (assertion: ScenarioAssertion) => void;
  onUpdateAssertion: (id: string, updates: Partial<ScenarioAssertion>) => void;
  onRemoveAssertion: (id: string) => void;
}

export function EditorSidePanel({
  content,
  selectedActorId,
  selectedStepId,
  onUpdateActor,
  onUpdateStep,
  onRemoveStep,
  onAddAssertion,
  onUpdateAssertion,
  onRemoveAssertion,
}: EditorSidePanelProps) {
  const [confirmDeleteOpen, setConfirmDeleteOpen] = React.useState(false);

  const selectedActor = selectedActorId
    ? content.actors.find((a) => a.id === selectedActorId) || null
    : null;
  const selectedStep = selectedStepId
    ? content.steps.find((s) => s.id === selectedStepId) || null
    : null;

  if (!selectedActor && !selectedStep) {
    return (
      <Box sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <Typography variant="body2" color="text.secondary">
          Select an actor or step to configure
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        {selectedStep && (
          <>
            <StepConfigForm
              step={selectedStep}
              onUpdate={(updates) => onUpdateStep(selectedStep.id, updates)}
            />
            <Divider />
            <AssertionPanel
              assertions={content.assertions}
              stepId={selectedStep.id}
              onAdd={onAddAssertion}
              onUpdate={onUpdateAssertion}
              onRemove={onRemoveAssertion}
            />
          </>
        )}
        {selectedActor && !selectedStep && (
          <ActorConfigForm
            actor={selectedActor}
            onUpdate={(updates) => onUpdateActor(selectedActor.id, updates)}
          />
        )}
      </Box>

      {selectedStep && (
        <>
          <Divider />
          <Box sx={{ p: 2 }}>
            <Button
              variant="outlined"
              color="error"
              size="small"
              fullWidth
              startIcon={<DeleteIcon />}
              onClick={() => setConfirmDeleteOpen(true)}
            >
              Delete Step
            </Button>
          </Box>

          <Dialog open={confirmDeleteOpen} onClose={() => setConfirmDeleteOpen(false)}>
            <DialogTitle>Delete Step</DialogTitle>
            <DialogContent>
              <DialogContentText>
                Delete the <strong>{ACTION_LABELS[selectedStep.action] || selectedStep.action}</strong> step?
                Any edges connected to it will also be removed.
              </DialogContentText>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setConfirmDeleteOpen(false)}>Cancel</Button>
              <Button
                color="error"
                onClick={() => {
                  setConfirmDeleteOpen(false);
                  onRemoveStep(selectedStep.id);
                }}
              >
                Delete
              </Button>
            </DialogActions>
          </Dialog>
        </>
      )}
    </Box>
  );
}
