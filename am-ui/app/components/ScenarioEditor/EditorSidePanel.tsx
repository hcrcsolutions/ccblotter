'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import type { ScenarioContent, ScenarioActor, ScenarioStep, ScenarioAssertion } from '../../types';
import { ActorConfigForm } from './ActorConfigForm';
import { StepConfigForm } from './StepConfigForm';
import { AssertionPanel } from './AssertionPanel';

interface EditorSidePanelProps {
  content: ScenarioContent;
  selectedActorId: string | null;
  selectedStepId: string | null;
  onUpdateActor: (actorId: string, updates: Partial<ScenarioActor>) => void;
  onUpdateStep: (stepId: string, updates: Partial<ScenarioStep>) => void;
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
  onAddAssertion,
  onUpdateAssertion,
  onRemoveAssertion,
}: EditorSidePanelProps) {
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
    <Box sx={{ height: '100%', overflow: 'auto' }}>
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
  );
}
