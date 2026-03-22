import type { ScenarioContent } from '../../types';
import { ACTION_DEP_REQUIREMENTS, ACTION_LABELS, isDepRequirementSatisfied } from './scenarioConstants';

export interface ValidationIssue {
  severity: 'error' | 'warning';
  stepId?: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  issues: ValidationIssue[];
}

export function validateScenario(content: ScenarioContent): ValidationResult {
  const issues: ValidationIssue[] = [];

  // 1. At least 1 actor
  if (content.actors.length === 0) {
    issues.push({ severity: 'error', message: 'At least 1 actor is required' });
  }

  // 2. At least 1 step
  if (content.steps.length === 0) {
    issues.push({ severity: 'error', message: 'At least 1 step is required' });
  }

  // 3. Every step's actorId references an existing actor
  const actorIds = new Set(content.actors.map((a) => a.id));
  for (const step of content.steps) {
    if (!actorIds.has(step.actorId)) {
      issues.push({
        severity: 'error',
        stepId: step.id,
        message: `Step references non-existent actor "${step.actorId}"`,
      });
    }
  }

  // 4. No dangling dependsOn references
  const stepIds = new Set(content.steps.map((s) => s.id));
  for (const step of content.steps) {
    for (const dep of step.dependsOn ?? []) {
      if (!stepIds.has(dep.stepId)) {
        issues.push({
          severity: 'error',
          stepId: step.id,
          message: `Step depends on non-existent step "${dep.stepId}"`,
        });
      }
    }
  }

  // 4b. Warn on same-actor SYNC edges
  for (const step of content.steps) {
    for (const dep of step.dependsOn ?? []) {
      if (dep.type === 'SYNC') {
        const sourceStep = content.steps.find((s) => s.id === dep.stepId);
        if (sourceStep && sourceStep.actorId === step.actorId) {
          issues.push({
            severity: 'warning',
            stepId: step.id,
            message: 'SYNC edge between steps of the same actor is unusual',
          });
        }
      }
    }
  }

  // 5. No dependency cycles (DFS-based cycle detection)
  const adj = new Map<string, string[]>();
  for (const step of content.steps) {
    adj.set(step.id, (step.dependsOn ?? []).map((d) => d.stepId));
  }
  const visited = new Set<string>();
  const inStack = new Set<string>();
  let hasCycle = false;

  function dfs(nodeId: string): void {
    if (hasCycle) {
      return;
    }
    visited.add(nodeId);
    inStack.add(nodeId);
    for (const dep of adj.get(nodeId) ?? []) {
      if (inStack.has(dep)) {
        hasCycle = true;
        return;
      }
      if (!visited.has(dep)) {
        dfs(dep);
      }
    }
    inStack.delete(nodeId);
  }

  for (const step of content.steps) {
    if (!visited.has(step.id)) {
      dfs(step.id);
    }
  }
  if (hasCycle) {
    issues.push({ severity: 'error', message: 'Dependency cycle detected' });
  }

  // 6. Required incoming dependencies per action type
  for (const step of content.steps) {
    const req = ACTION_DEP_REQUIREMENTS[step.action];
    if (req && !isDepRequirementSatisfied(step, content.steps)) {
      const actionLabel = ACTION_LABELS[step.action] || step.action;
      issues.push({
        severity: 'error',
        stepId: step.id,
        message: `${actionLabel} requires: ${req.label}`,
      });
    }
  }

  // 7. Warn when a non-first step in a swim lane has no same-actor predecessor
  const stepMap = new Map(content.steps.map((s) => [s.id, s]));
  for (const actor of content.actors) {
    const laneSteps = content.steps
      .filter((s) => s.actorId === actor.id)
      .sort((a, b) => (a.position?.y ?? 0) - (b.position?.y ?? 0));
    for (let i = 1; i < laneSteps.length; i++) {
      const step = laneSteps[i];
      const hasSameActorPredecessor = (step.dependsOn ?? []).some((dep) => {
        const source = stepMap.get(dep.stepId);
        return source && source.actorId === actor.id;
      });
      if (!hasSameActorPredecessor) {
        const actionLabel = ACTION_LABELS[step.action] || step.action;
        issues.push({
          severity: 'warning',
          stepId: step.id,
          message: `${actionLabel} has no predecessor in ${actor.name}'s lane — execution order is not guaranteed`,
        });
      }
    }
  }

  // 8. At least 1 assertion
  if (content.assertions.length === 0) {
    issues.push({ severity: 'warning', message: 'No assertions defined' });
  }

  return {
    valid: issues.filter((i) => i.severity === 'error').length === 0,
    issues,
  };
}
