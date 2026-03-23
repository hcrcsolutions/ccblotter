import type { ActorRole, ScenarioAction, CallerProfile, AgentProfile, AssertionType, EdgeType, ScenarioStep } from '../../types';

export const ROLE_COLORS: Record<ActorRole, string> = {
  CALLER: '#2196f3',
  AGENT: '#4caf50',
  SUPERVISOR: '#ff9800',
};

export const ROLE_LABELS: Record<ActorRole, string> = {
  CALLER: 'Caller',
  AGENT: 'Agent',
  SUPERVISOR: 'Supervisor',
};

export const CALLER_ACTIONS: ScenarioAction[] = ['DIAL_IN', 'IVR_INPUT', 'WAIT_IN_QUEUE', 'HANG_UP', 'WAIT'];
export const AGENT_ACTIONS: ScenarioAction[] = ['LOGIN', 'GO_ONLINE', 'GO_AWAY', 'ANSWER_CALL', 'HOLD_CALL', 'RESUME_CALL', 'END_CALL', 'LOGOUT', 'WAIT'];
export const SUPERVISOR_ACTIONS: ScenarioAction[] = ['LOGIN', 'MONITOR', 'WHISPER', 'BARGE_IN', 'END_MONITOR', 'WAIT'];

export const ACTIONS_BY_ROLE: Record<ActorRole, ScenarioAction[]> = {
  CALLER: CALLER_ACTIONS,
  AGENT: AGENT_ACTIONS,
  SUPERVISOR: SUPERVISOR_ACTIONS,
};

export const ACTION_LABELS: Record<string, string> = {
  DIAL_IN: 'Dial In',
  IVR_INPUT: 'IVR Input',
  WAIT_IN_QUEUE: 'Wait in Queue',
  HANG_UP: 'Hang Up',
  LOGIN: 'Login',
  GO_ONLINE: 'Go Online',
  GO_AWAY: 'Go Away',
  ANSWER_CALL: 'Answer Call',
  HOLD_CALL: 'Hold Call',
  RESUME_CALL: 'Resume Call',
  END_CALL: 'End Call',
  LOGOUT: 'Logout',
  MONITOR: 'Monitor',
  WHISPER: 'Whisper',
  BARGE_IN: 'Barge In',
  END_MONITOR: 'End Monitor',
  WAIT: 'Wait',
};

export const ACTION_ICONS: Record<string, string> = {
  DIAL_IN: 'phone',
  IVR_INPUT: 'dialpad',
  WAIT_IN_QUEUE: 'hourglass_empty',
  HANG_UP: 'call_end',
  LOGIN: 'login',
  GO_ONLINE: 'circle',
  GO_AWAY: 'do_not_disturb',
  ANSWER_CALL: 'phone_callback',
  HOLD_CALL: 'pause',
  RESUME_CALL: 'play_arrow',
  END_CALL: 'call_end',
  LOGOUT: 'logout',
  MONITOR: 'visibility',
  WHISPER: 'record_voice_over',
  BARGE_IN: 'phone_in_talk',
  END_MONITOR: 'visibility_off',
  WAIT: 'timer',
};

export const CALLER_PROFILES: { value: CallerProfile; label: string; description: string }[] = [
  { value: 'PATIENT', label: 'Patient', description: 'Waits indefinitely in queue' },
  { value: 'IMPATIENT', label: 'Impatient', description: 'Hangs up after configurable timeout' },
  { value: 'RANDOM_INPUT', label: 'Random Input', description: 'Random DTMF digits at IVR inputs' },
];

export const AGENT_PROFILES: { value: AgentProfile; label: string; description: string }[] = [
  { value: 'EFFICIENT', label: 'Efficient', description: 'No delay before actions' },
  { value: 'NORMAL', label: 'Normal', description: '1-3s random delay' },
  { value: 'SLOW', label: 'Slow', description: '3-8s random delay' },
];

export const ASSERTION_TYPE_LABELS: Record<AssertionType, string> = {
  AGENT_IN_STATE: 'Agent in State',
  CALL_EXISTS: 'Call Exists',
  CALL_CONNECTED_WITHIN: 'Call Connected Within',
  QUEUE_LENGTH: 'Queue Length',
  IVR_REACHED_NODE: 'IVR Reached Node',
};

export const EDGE_TYPE_LABELS: Record<EdgeType, string> = {
  SEQUENCE: 'Sequence (happens after)',
  SYNC: 'Sync (synchronize with)',
  TRIGGER: 'Trigger (causes)',
  WAIT_FOR: 'Wait For (block until)',
};

export const EDGE_TYPE_SHORT_LABELS: Record<EdgeType, string> = {
  SEQUENCE: 'then',
  SYNC: 'sync',
  TRIGGER: 'triggers',
  WAIT_FOR: 'wait for',
};

export interface EdgeTypeStyle {
  color: string;
  strokeDasharray: string | undefined;
  strokeWidth: number;
}

export const EDGE_TYPE_STYLES: Record<EdgeType, EdgeTypeStyle> = {
  SEQUENCE: { color: '#757575', strokeDasharray: undefined, strokeWidth: 1.5 },
  SYNC: { color: '#1976d2', strokeDasharray: '6 3', strokeWidth: 1.5 },
  TRIGGER: { color: '#e65100', strokeDasharray: '2 3', strokeWidth: 1.5 },
  WAIT_FOR: { color: '#7b1fa2', strokeDasharray: '8 3 2 3', strokeWidth: 1.5 },
};

// ---------------------------------------------------------------------------
// Required incoming dependency per action type
// ---------------------------------------------------------------------------

export interface ActionDepRequirement {
  sourceActions: ScenarioAction[];
  edgeTypes?: EdgeType[];
  sameActor?: boolean;
  label: string;
}

export const ACTION_DEP_REQUIREMENTS: Partial<Record<ScenarioAction, ActionDepRequirement>> = {
  ANSWER_CALL: { sourceActions: ['DIAL_IN'], sameActor: false, label: 'Dependency from a Dial In step (different actor)' },
  WAIT_IN_QUEUE: { sourceActions: ['DIAL_IN'], sameActor: false, label: 'Dependency from a Dial In step (different actor)' },
  HOLD_CALL: { sourceActions: ['ANSWER_CALL'], label: 'Dependency from an Answer Call step' },
  RESUME_CALL: { sourceActions: ['HOLD_CALL'], label: 'Dependency from a Hold Call step' },
  END_CALL: { sourceActions: ['ANSWER_CALL', 'RESUME_CALL'], label: 'Dependency from an Answer Call or Resume Call step' },
  IVR_INPUT: { sourceActions: ['DIAL_IN'], label: 'Dependency from a Dial In step' },
  MONITOR: { sourceActions: ['ANSWER_CALL'], sameActor: false, label: 'Dependency from an Answer Call step (different actor)' },
  WHISPER: { sourceActions: ['MONITOR'], sameActor: true, label: 'Sequence from a Monitor step (same actor)' },
  BARGE_IN: { sourceActions: ['MONITOR'], sameActor: true, label: 'Sequence from a Monitor step (same actor)' },
  END_MONITOR: { sourceActions: ['MONITOR', 'WHISPER', 'BARGE_IN'], sameActor: true, label: 'Sequence from a monitoring step (same actor)' },
  GO_ONLINE: { sourceActions: ['LOGIN'], sameActor: true, label: 'Sequence from a Login step (same actor)' },
  GO_AWAY: { sourceActions: ['LOGIN', 'GO_ONLINE'], sameActor: true, label: 'Sequence from a Login or Go Online step (same actor)' },
  LOGOUT: { sourceActions: ['LOGIN', 'GO_ONLINE', 'GO_AWAY'], sameActor: true, label: 'Sequence from a logged-in step (same actor)' },
};

/**
 * Check whether a step's incoming dependencies satisfy its ActionDepRequirement.
 * Returns `true` if the requirement is met (or no requirement exists).
 */
export function isDepRequirementSatisfied(
  step: ScenarioStep,
  allSteps: ScenarioStep[],
): boolean {
  const req = ACTION_DEP_REQUIREMENTS[step.action];
  if (!req) {
    return true;
  }

  const stepMap = new Map<string, ScenarioStep>();
  for (const s of allSteps) {
    stepMap.set(s.id, s);
  }

  // Check explicit edges
  for (const dep of step.dependsOn ?? []) {
    const source = stepMap.get(dep.stepId);
    if (!source) {
      continue;
    }
    if (!req.sourceActions.includes(source.action)) {
      continue;
    }
    if (req.edgeTypes && !req.edgeTypes.includes(dep.type)) {
      continue;
    }
    if (req.sameActor === true && source.actorId !== step.actorId) {
      continue;
    }
    if (req.sameActor === false && source.actorId === step.actorId) {
      continue;
    }
    return true;
  }

  // For same-actor requirements, swim-lane position implies chronological order:
  // if a matching source action exists above this step in the same lane, treat as satisfied.
  if (req.sameActor === true) {
    const stepY = step.position?.y ?? Infinity;
    for (const s of allSteps) {
      if (s.id === step.id || s.actorId !== step.actorId) {
        continue;
      }
      if (!req.sourceActions.includes(s.action)) {
        continue;
      }
      const sourceY = s.position?.y ?? Infinity;
      if (sourceY < stepY) {
        return true;
      }
    }
  }

  return false;
}

export const DEFAULT_SCENARIO_CONTENT = {
  actors: [],
  steps: [],
  assertions: [],
  settings: {
    defaultTimingMode: 'COMPRESSED' as const,
    compressedTimeScale: 10,
    cleanupAfterRun: true,
  },
};
