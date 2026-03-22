import * as React from 'react';
import {
  type Node,
  type Edge,
  type Connection,
  type NodeChange,
  type EdgeChange,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
} from '@xyflow/react';
import { getBackendUrl } from '../../lib/settings';
import { ACTION_DEP_REQUIREMENTS, DEFAULT_SCENARIO_CONTENT, EDGE_TYPE_SHORT_LABELS, isDepRequirementSatisfied } from './scenarioConstants';
import { validateScenario, type ValidationResult } from './validateScenario';
import type { StepNodeData } from './ScenarioStepNode';
import type {
  ScenarioContent,
  ScenarioActor,
  ScenarioStep,
  ScenarioAssertion,
  ScenarioSettings,
  EdgeType,
  StepDependency,
} from '../../types';

/** Width of each actor swim lane in flow coordinates. */
export const LANE_WIDTH = 220;
/** Horizontal gap between lanes. */
const LANE_GAP = 20;
/** Total lane pitch (width + gap). */
export const LANE_PITCH = LANE_WIDTH + LANE_GAP;
/** Vertical spacing when auto-positioning steps. */
const AUTO_ROW_HEIGHT = 110;
/** Node width (must match ScenarioStepNode). */
const NODE_WIDTH = 180;
/** Y offset so nodes don't overlap lane headers. */
export const LANE_HEADER_HEIGHT = 56;
/** X position for a node inside a given actor lane. */
function laneX(actorIdx: number): number {
  return actorIdx * LANE_PITCH + (LANE_WIDTH - NODE_WIDTH) / 2;
}

interface UseScenarioEditorReturn {
  content: ScenarioContent;
  nodes: Node[];
  edges: Edge[];
  loading: boolean;
  saving: boolean;
  dirty: boolean;
  error: string | null;
  version: number;
  validation: ValidationResult;
  selectedActorId: string | null;
  selectedStepId: string | null;
  setSelectedActorId: (id: string | null) => void;
  setSelectedStepId: (id: string | null) => void;
  onNodesChange: (changes: NodeChange[]) => void;
  onEdgesChange: (changes: EdgeChange[]) => void;
  onConnect: (connection: Connection) => void;
  addActor: (actor: ScenarioActor) => void;
  updateActor: (actorId: string, updates: Partial<ScenarioActor>) => void;
  removeActor: (actorId: string) => void;
  addStep: (step: ScenarioStep) => void;
  updateStep: (stepId: string, updates: Partial<ScenarioStep>) => void;
  removeStep: (stepId: string) => void;
  addAssertion: (assertion: ScenarioAssertion) => void;
  updateAssertion: (assertionId: string, updates: Partial<ScenarioAssertion>) => void;
  removeAssertion: (assertionId: string) => void;
  updateSettings: (updates: Partial<ScenarioSettings>) => void;
  updateEdgeType: (sourceId: string, targetId: string, newType: EdgeType) => void;
  save: () => Promise<void>;
  clearError: () => void;
}

function autoPosition(
  step: ScenarioStep,
  actors: ScenarioActor[],
  steps: ScenarioStep[],
): { x: number; y: number } {
  const actorIdx = actors.findIndex((a) => a.id === step.actorId);
  const actorSteps = steps.filter((s) => s.actorId === step.actorId && s.id !== step.id);
  return {
    x: laneX(Math.max(0, actorIdx)),
    y: LANE_HEADER_HEIGHT + actorSteps.length * AUTO_ROW_HEIGHT,
  };
}

function deriveNodes(content: ScenarioContent): Node[] {
  return content.steps.map((step) => {
    const actor = content.actors.find((a) => a.id === step.actorId);
    const assertionCount = content.assertions.filter((a) => a.afterStepId === step.id).length;
    const hasDepError = !!ACTION_DEP_REQUIREMENTS[step.action]
      && !isDepRequirementSatisfied(step, content.steps);
    const nodeData: StepNodeData = {
      step,
      actorName: actor?.name ?? 'Unknown',
      actorRole: actor?.role ?? 'CALLER',
      assertionCount,
      hasDepError,
    };
    return {
      id: step.id,
      type: 'scenarioStep',
      position: step.position ?? autoPosition(step, content.actors, content.steps),
      data: nodeData,
    };
  });
}

function inferEdgeLabel(
  sourceStep: ScenarioStep,
  targetStep: ScenarioStep,
  sourceActor: ScenarioActor | undefined,
  edgeType: EdgeType,
): string {
  // Context-aware labels for well-known action pairs
  if (sourceStep.action === 'DIAL_IN' && targetStep.action === 'ANSWER_CALL') {
    return `${sourceActor?.name ?? 'Caller'}'s call`;
  }
  if (sourceStep.action === 'DIAL_IN' && targetStep.action === 'WAIT_IN_QUEUE') {
    return 'enters queue';
  }
  if (sourceStep.action === 'ANSWER_CALL' && targetStep.action === 'HOLD_CALL') {
    return 'active call';
  }
  if (sourceStep.action === 'HOLD_CALL' && targetStep.action === 'RESUME_CALL') {
    return 'held call';
  }
  // Fall back to edge type short label
  return EDGE_TYPE_SHORT_LABELS[edgeType];
}

function inferEdgeType(
  sourceStep: ScenarioStep,
  targetStep: ScenarioStep,
): EdgeType {
  if (sourceStep.action === 'DIAL_IN' && targetStep.action === 'ANSWER_CALL') {
    return 'SYNC';
  }
  if (sourceStep.action === 'DIAL_IN' && targetStep.action === 'WAIT_IN_QUEUE') {
    return 'TRIGGER';
  }
  if (sourceStep.actorId === targetStep.actorId) {
    return 'SEQUENCE';
  }
  return 'WAIT_FOR';
}

function deriveEdges(content: ScenarioContent): Edge[] {
  const actorIndexMap = new Map<string, number>();
  content.actors.forEach((a, i) => actorIndexMap.set(a.id, i));

  const stepMap = new Map<string, ScenarioStep>();
  for (const s of content.steps) {
    stepMap.set(s.id, s);
  }

  const actorMap = new Map<string, ScenarioActor>();
  for (const a of content.actors) {
    actorMap.set(a.id, a);
  }

  return content.steps.flatMap((step) =>
    (step.dependsOn ?? []).map((dep) => {
      const depId = dep.stepId;
      const edgeType = dep.type ?? 'SEQUENCE';
      const sourceStep = stepMap.get(depId);
      const sameActor = sourceStep && sourceStep.actorId === step.actorId;
      const sourceActorIdx = actorIndexMap.get(sourceStep?.actorId ?? '') ?? 0;
      const targetActorIdx = actorIndexMap.get(step.actorId) ?? 0;

      // Same-lane edges route through top/bottom; cross-lane through left/right
      let sourceHandle: string;
      let targetHandle: string;
      if (sameActor) {
        sourceHandle = 'bottom';
        targetHandle = 'top';
      } else {
        sourceHandle = sourceActorIdx <= targetActorIdx ? 'right-bottom' : 'left-bottom';
        targetHandle = sourceActorIdx <= targetActorIdx ? 'left-top' : 'right-top';
      }

      const label = sourceStep
        ? inferEdgeLabel(sourceStep, step, actorMap.get(sourceStep.actorId), edgeType)
        : EDGE_TYPE_SHORT_LABELS[edgeType];

      return {
        id: `e-${depId}-${step.id}`,
        source: depId,
        target: step.id,
        sourceHandle,
        targetHandle,
        type: 'scenarioDependency',
        data: { label, edgeType },
      };
    }),
  );
}

export function useScenarioEditor(scenarioId: string): UseScenarioEditorReturn {
  const [content, setContent] = React.useState<ScenarioContent>(DEFAULT_SCENARIO_CONTENT);
  const [loading, setLoading] = React.useState(true);
  const [saving, setSaving] = React.useState(false);
  const [dirty, setDirty] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [version, setVersion] = React.useState(0);
  const [selectedActorId, setSelectedActorId] = React.useState<string | null>(null);
  const [selectedStepId, setSelectedStepId] = React.useState<string | null>(null);

  // React Flow nodes/edges derived from content
  const [nodes, setNodes] = React.useState<Node[]>([]);
  const [edges, setEdges] = React.useState<Edge[]>([]);

  // Ref for reading content inside callbacks without stale closures
  const contentRef = React.useRef(content);
  contentRef.current = content;

  // Validation — recomputed on every content change
  const validation = React.useMemo(() => validateScenario(content), [content]);

  // Load content
  React.useEffect(() => {
    async function loadContent() {
      try {
        const res = await fetch(`${getBackendUrl()}/api/v1/test-scenarios/${scenarioId}/content`);
        if (!res.ok) {
          throw new Error('Failed to load scenario content');
        }
        const data = await res.json();
        setVersion(data.version);
        if (data.content) {
          const parsed = typeof data.content === 'string' ? JSON.parse(data.content) : data.content;
          setContent({
            actors: parsed.actors || [],
            steps: (parsed.steps || []).map((s: Record<string, unknown>) => {
              // Migrate legacy dependsOn: string[] to StepDependency[]
              const rawDeps = s.dependsOn as unknown[] | undefined;
              const normalizedDeps: StepDependency[] | undefined = rawDeps
                ? rawDeps.map((d: unknown) => {
                    if (typeof d === 'string') {
                      return { stepId: d, type: 'SEQUENCE' as EdgeType };
                    }
                    return d as StepDependency;
                  })
                : undefined;
              return {
                ...s,
                delayMs: (s.delayMs as number) ?? (s.timeOffsetMs as number) ?? 0,
                timeOffsetMs: undefined,
                dependsOn: normalizedDeps,
              };
            }),
            assertions: parsed.assertions || [],
            settings: {
              ...DEFAULT_SCENARIO_CONTENT.settings,
              ...parsed.settings,
            },
          });
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load content');
      } finally {
        setLoading(false);
      }
    }
    loadContent();
  }, [scenarioId]);

  // Derive nodes/edges whenever content changes
  React.useEffect(() => {
    setNodes(deriveNodes(content));
    setEdges(deriveEdges(content));
  }, [content]);

  const updateContent = React.useCallback((updater: (prev: ScenarioContent) => ScenarioContent) => {
    setContent((prev) => {
      const next = updater(prev);
      setDirty(true);
      return next;
    });
  }, []);

  // --- React Flow handlers ---

  const onNodesChange = React.useCallback((changes: NodeChange[]) => {
    setNodes((prev) => {
      const currentActors = contentRef.current.actors;

      // For position changes, snap x to the node's actor lane
      const snapped = changes.map((c) => {
        if (c.type === 'position' && 'position' in c && c.position) {
          const node = prev.find((n) => n.id === c.id);
          if (node) {
            const stepData = node.data as StepNodeData;
            const actorIdx = currentActors.findIndex((a) => a.id === stepData.step.actorId);
            const snappedX = laneX(Math.max(0, actorIdx));
            return { ...c, position: { x: snappedX, y: Math.max(LANE_HEADER_HEIGHT, c.position.y) } };
          }
        }
        return c;
      });

      const updated = applyNodeChanges(snapped, prev);

      // On drag-end: persist positions only (no timing mutation)
      const dragEndChanges = snapped.filter(
        (c) => c.type === 'position' && 'dragging' in c && c.dragging === false,
      );
      if (dragEndChanges.length > 0) {
        setContent((prevContent) => {
          const newSteps = prevContent.steps.map((step) => {
            const node = updated.find((n) => n.id === step.id);
            if (node) {
              return { ...step, position: { x: node.position.x, y: node.position.y } };
            }
            return step;
          });
          return { ...prevContent, steps: newSteps };
        });
        setDirty(true);
        return updated;
      }

      // During drag (not ended yet): just persist positions
      const positionChanges = snapped.filter(
        (c) => c.type === 'position' && 'position' in c && c.position,
      );
      if (positionChanges.length > 0) {
        setContent((prevContent) => {
          const newSteps = prevContent.steps.map((step) => {
            const change = positionChanges.find(
              (c) => c.type === 'position' && c.id === step.id,
            );
            if (change && change.type === 'position' && 'position' in change && change.position) {
              return { ...step, position: change.position as { x: number; y: number } };
            }
            return step;
          });
          return { ...prevContent, steps: newSteps };
        });
        setDirty(true);
      }

      // Handle node removals (delete key)
      const removeChanges = changes.filter((c) => c.type === 'remove');
      if (removeChanges.length > 0) {
        const removedIds = new Set(removeChanges.map((c) => c.id));
        setContent((prevContent) => ({
          ...prevContent,
          steps: prevContent.steps.filter((s) => !removedIds.has(s.id)),
          assertions: prevContent.assertions.filter((a) => !removedIds.has(a.afterStepId)),
        }));
        setDirty(true);
        setSelectedStepId((p) => (p && removedIds.has(p) ? null : p));
      }
      return updated;
    });
  }, []);

  const onEdgesChange = React.useCallback((changes: EdgeChange[]) => {
    setEdges((prev) => {
      const updated = applyEdgeChanges(changes, prev);
      const removeChanges = changes.filter((c) => c.type === 'remove');
      if (removeChanges.length > 0) {
        const removedEdgeIds = new Set(removeChanges.map((c) => c.id));
        const removedPairs: { source: string; target: string }[] = [];
        for (const edgeId of removedEdgeIds) {
          const edge = prev.find((e) => e.id === edgeId);
          if (edge) {
            removedPairs.push({ source: edge.source, target: edge.target });
          }
        }
        if (removedPairs.length > 0) {
          setContent((prevContent) => ({
            ...prevContent,
            steps: prevContent.steps.map((step) => {
              const toRemove = removedPairs
                .filter((p) => p.target === step.id)
                .map((p) => p.source);
              if (toRemove.length === 0 || !step.dependsOn) {
                return step;
              }
              const newDeps = step.dependsOn.filter((d) => !toRemove.includes(d.stepId));
              return { ...step, dependsOn: newDeps.length > 0 ? newDeps : undefined };
            }),
          }));
          setDirty(true);
        }
      }
      return updated;
    });
  }, []);

  const onConnect = React.useCallback((connection: Connection) => {
    if (!connection.source || !connection.target) {
      return;
    }
    const sourceId = connection.source;
    const targetId = connection.target;

    const isVerticalHandle = connection.sourceHandle === 'top'
      || connection.sourceHandle === 'bottom'
      || connection.targetHandle === 'top'
      || connection.targetHandle === 'bottom';

    // Top/bottom handles are restricted to same-lane connections
    if (isVerticalHandle) {
      const sourceStep = contentRef.current.steps.find((s) => s.id === sourceId);
      const targetStep = contentRef.current.steps.find((s) => s.id === targetId);
      if (sourceStep && targetStep && sourceStep.actorId !== targetStep.actorId) {
        return;
      }
    }

    const edgeId = `e-${sourceId}-${targetId}`;

    setContent((prevContent) => {
      const sourceStep = prevContent.steps.find((s) => s.id === sourceId);
      const targetStep = prevContent.steps.find((s) => s.id === targetId);

      // Vertical handles always produce SEQUENCE edges
      const edgeType: EdgeType = isVerticalHandle
        ? 'SEQUENCE'
        : sourceStep && targetStep
          ? inferEdgeType(sourceStep, targetStep)
          : 'SEQUENCE';

      return {
        ...prevContent,
        steps: prevContent.steps.map((step) => {
          if (step.id === targetId) {
            const deps = step.dependsOn ?? [];
            if (!deps.some((d) => d.stepId === sourceId)) {
              return { ...step, dependsOn: [...deps, { stepId: sourceId, type: edgeType }] };
            }
          }
          return step;
        }),
      };
    });

    setEdges((prev) => addEdge({ ...connection, id: edgeId, type: 'scenarioDependency' }, prev));
    setDirty(true);
  }, []);

  // --- Content CRUD ---

  const addActor = React.useCallback((actor: ScenarioActor) => {
    updateContent((prev) => ({ ...prev, actors: [...prev.actors, actor] }));
  }, [updateContent]);

  const updateActor = React.useCallback((actorId: string, updates: Partial<ScenarioActor>) => {
    updateContent((prev) => ({
      ...prev,
      actors: prev.actors.map((a) => (a.id === actorId ? { ...a, ...updates } : a)),
    }));
  }, [updateContent]);

  const removeActor = React.useCallback((actorId: string) => {
    updateContent((prev) => ({
      ...prev,
      actors: prev.actors.filter((a) => a.id !== actorId),
      steps: prev.steps.filter((s) => s.actorId !== actorId),
      assertions: prev.assertions.filter((a) => {
        const step = prev.steps.find((s) => s.id === a.afterStepId);
        return step ? step.actorId !== actorId : true;
      }),
    }));
    setSelectedActorId(null);
    setSelectedStepId(null);
  }, [updateContent]);

  const addStep = React.useCallback((step: ScenarioStep) => {
    setContent((prev) => {
      const pos = step.position ?? autoPosition(step, prev.actors, prev.steps);
      const newStep = { ...step, position: pos };
      setDirty(true);
      return { ...prev, steps: [...prev.steps, newStep] };
    });
  }, []);

  const updateStep = React.useCallback((stepId: string, updates: Partial<ScenarioStep>) => {
    updateContent((prev) => ({
      ...prev,
      steps: prev.steps.map((s) => (s.id === stepId ? { ...s, ...updates } : s)),
    }));
  }, [updateContent]);

  const removeStep = React.useCallback((stepId: string) => {
    updateContent((prev) => ({
      ...prev,
      steps: prev.steps.filter((s) => s.id !== stepId),
      assertions: prev.assertions.filter((a) => a.afterStepId !== stepId),
    }));
    setSelectedStepId(null);
  }, [updateContent]);

  const addAssertion = React.useCallback((assertion: ScenarioAssertion) => {
    updateContent((prev) => ({ ...prev, assertions: [...prev.assertions, assertion] }));
  }, [updateContent]);

  const updateAssertion = React.useCallback((assertionId: string, updates: Partial<ScenarioAssertion>) => {
    updateContent((prev) => ({
      ...prev,
      assertions: prev.assertions.map((a) => (a.id === assertionId ? { ...a, ...updates } : a)),
    }));
  }, [updateContent]);

  const removeAssertion = React.useCallback((assertionId: string) => {
    updateContent((prev) => ({
      ...prev,
      assertions: prev.assertions.filter((a) => a.id !== assertionId),
    }));
  }, [updateContent]);

  const updateSettings = React.useCallback((updates: Partial<ScenarioSettings>) => {
    updateContent((prev) => ({
      ...prev,
      settings: { ...prev.settings, ...updates },
    }));
  }, [updateContent]);

  const updateEdgeType = React.useCallback((sourceId: string, targetId: string, newType: EdgeType) => {
    updateContent((prev) => ({
      ...prev,
      steps: prev.steps.map((step) => {
        if (step.id === targetId && step.dependsOn) {
          return {
            ...step,
            dependsOn: step.dependsOn.map((d) =>
              d.stepId === sourceId ? { ...d, type: newType } : d,
            ),
          };
        }
        return step;
      }),
    }));
  }, [updateContent]);

  const save = React.useCallback(async () => {
    setSaving(true);
    setError(null);
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/test-scenarios/${scenarioId}/content`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content }),
      });
      if (!res.ok) {
        throw new Error('Failed to save scenario content');
      }
      const data = await res.json();
      setVersion(data.version);
      setDirty(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to save');
    } finally {
      setSaving(false);
    }
  }, [scenarioId, content]);

  const clearError = React.useCallback(() => setError(null), []);

  return {
    content,
    nodes,
    edges,
    loading,
    saving,
    dirty,
    error,
    version,
    validation,
    selectedActorId,
    selectedStepId,
    setSelectedActorId,
    setSelectedStepId,
    onNodesChange,
    onEdgesChange,
    onConnect,
    addActor,
    updateActor,
    removeActor,
    addStep,
    updateStep,
    removeStep,
    addAssertion,
    updateAssertion,
    removeAssertion,
    updateSettings,
    updateEdgeType,
    save,
    clearError,
  };
}
