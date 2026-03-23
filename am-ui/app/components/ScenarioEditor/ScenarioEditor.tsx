'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import LinearProgress from '@mui/material/LinearProgress';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import {
  ReactFlow,
  ConnectionMode,
  Background,
  Controls,
  MiniMap,
  type NodeTypes,
  type EdgeTypes,
  type Edge,
  type MiniMapNodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import type { EdgeType } from '../../types';
import { getBackendUrl } from '../../lib/settings';
import { useScenarioEditor } from './useScenarioEditor';
import { EDGE_TYPE_LABELS, EDGE_TYPE_STYLES, ROLE_COLORS } from './scenarioConstants';
import type { BookendNodeData } from './ScenarioBookendNode';
import { ScenarioToolbar } from './ScenarioToolbar';
import { EditorSidePanel } from './EditorSidePanel';
import { AddActorDialog } from './AddActorDialog';
import { AddStepDialog } from './AddStepDialog';
import { RunResultsPanel } from './RunResultsPanel';
import { TranscriptViewer } from './TranscriptViewer';
import { RunHistoryDialog } from './RunHistoryDialog';
import { ScenarioStepNode } from './ScenarioStepNode';
import { ScenarioBookendNode } from './ScenarioBookendNode';
import { ScenarioDependencyEdge } from './ScenarioDependencyEdge';
import { SwimLaneBackground } from './SwimLaneBackground';
import { EdgeTypeLegend } from './EdgeTypeLegend';

const nodeTypes: NodeTypes = {
  scenarioStep: ScenarioStepNode,
  scenarioBookend: ScenarioBookendNode,
};

const edgeTypes: EdgeTypes = {
  scenarioDependency: ScenarioDependencyEdge,
};

const EDGE_TYPES: EdgeType[] = ['SEQUENCE', 'SYNC', 'TRIGGER', 'WAIT_FOR'];

function MiniMapNodeRenderer({ id, x, y, width, height, color, strokeColor, strokeWidth }: MiniMapNodeProps) {
  const isBookend = id.startsWith('start-') || id.startsWith('end-');
  if (isBookend) {
    const pw = width * 0.5;
    const ph = height * 0.6;
    return (
      <rect
        x={x + (width - pw) / 2}
        y={y + (height - ph) / 2}
        width={pw}
        height={ph}
        rx={ph / 2}
        ry={ph / 2}
        fill={color}
        stroke={strokeColor}
        strokeWidth={strokeWidth}
      />
    );
  }
  return (
    <rect
      x={x}
      y={y}
      width={width}
      height={height}
      rx={5}
      ry={5}
      fill={color}
      stroke={strokeColor}
      strokeWidth={strokeWidth}
    />
  );
}

function miniMapNodeColor(node: { type?: string; data: Record<string, unknown> }): string {
  if (node.type === 'scenarioBookend') {
    const data = node.data as BookendNodeData;
    return ROLE_COLORS[data.actorRole];
  }
  return '#e2e2e2';
}

interface EdgeContextMenuState {
  mouseX: number;
  mouseY: number;
  sourceId: string;
  targetId: string;
}

interface ScenarioEditorProps {
  scenario: {
    id: string;
    name: string;
    status: string;
    description: string | null;
  };
}

export function ScenarioEditor({ scenario }: ScenarioEditorProps) {
  const editor = useScenarioEditor(scenario.id);
  const [addActorOpen, setAddActorOpen] = React.useState(false);
  const [addStepOpen, setAddStepOpen] = React.useState(false);
  const [activeRunId, setActiveRunId] = React.useState<string | null>(null);
  const [transcriptOpen, setTranscriptOpen] = React.useState(false);
  const [historyOpen, setHistoryOpen] = React.useState(false);
  const [runError, setRunError] = React.useState<string | null>(null);
  const [edgeContextMenu, setEdgeContextMenu] = React.useState<EdgeContextMenuState | null>(null);

  const handleRun = async () => {
    setRunError(null);
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/test-scenarios/${scenario.id}/run`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ timingMode: editor.content.settings.defaultTimingMode }),
      });
      if (!res.ok) {
        throw new Error('Failed to start execution');
      }
      const data = await res.json();
      setActiveRunId(data.id);
    } catch (e) {
      setRunError(e instanceof Error ? e.message : 'Failed to start execution');
    }
  };

  const handleNodeClick = React.useCallback(
    (_: React.MouseEvent, node: { id: string }) => {
      const step = editor.content.steps.find((s) => s.id === node.id);
      if (step) {
        editor.setSelectedStepId(step.id);
        editor.setSelectedActorId(step.actorId);
      }
    },
    [editor],
  );

  const handlePaneClick = React.useCallback(() => {
    editor.setSelectedStepId(null);
    editor.setSelectedActorId(null);
  }, [editor]);

  const handleSelectActor = React.useCallback(
    (actorId: string) => {
      editor.setSelectedActorId(actorId);
      editor.setSelectedStepId(null);
    },
    [editor],
  );

  const handleEdgeContextMenu = React.useCallback(
    (event: React.MouseEvent, edge: Edge) => {
      event.preventDefault();
      setEdgeContextMenu({
        mouseX: event.clientX,
        mouseY: event.clientY,
        sourceId: edge.source,
        targetId: edge.target,
      });
    },
    [],
  );

  const handleEdgeTypeSelect = React.useCallback(
    (newType: EdgeType) => {
      if (edgeContextMenu) {
        editor.updateEdgeType(edgeContextMenu.sourceId, edgeContextMenu.targetId, newType);
      }
      setEdgeContextMenu(null);
    },
    [edgeContextMenu, editor],
  );

  if (editor.loading) {
    return (
      <Box sx={{ p: 3 }}>
        <LinearProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      <ScenarioToolbar
        name={scenario.name}
        version={editor.version}
        dirty={editor.dirty}
        saving={editor.saving}
        validation={editor.validation}
        onSave={editor.save}
        onRun={handleRun}
        onHistory={() => setHistoryOpen(true)}
        onAddActor={() => setAddActorOpen(true)}
        onAddStep={() => setAddStepOpen(true)}
        onTidyLayout={editor.tidyLayout}
      />

      {editor.error && (
        <Alert severity="error" sx={{ m: 1 }} onClose={editor.clearError}>
          {editor.error}
        </Alert>
      )}

      {runError && (
        <Alert severity="error" sx={{ m: 1 }} onClose={() => setRunError(null)}>
          {runError}
        </Alert>
      )}

      <Box sx={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* React Flow canvas with swim lanes */}
        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
          <Box sx={{ flex: 1 }}>
            <ReactFlow
              nodes={editor.nodes}
              edges={editor.edges}
              onNodesChange={editor.onNodesChange}
              onEdgesChange={editor.onEdgesChange}
              onConnect={editor.onConnect}
              onNodeClick={handleNodeClick}
              onPaneClick={handlePaneClick}
              onEdgeContextMenu={handleEdgeContextMenu}
              nodeTypes={nodeTypes}
              edgeTypes={edgeTypes}
              connectionMode={ConnectionMode.Loose}
              fitView
              proOptions={{ hideAttribution: true }}
              deleteKeyCode={['Backspace', 'Delete']}
              defaultEdgeOptions={{
                type: 'scenarioDependency',
                selectable: true,
                deletable: true,
              }}
            >
              <SwimLaneBackground
                actors={editor.content.actors}
                selectedActorId={editor.selectedActorId}
                onSelectActor={handleSelectActor}
                onDeleteActor={editor.removeActor}
              />
              <Background />
              <Controls />
              <MiniMap nodeColor={miniMapNodeColor} nodeComponent={MiniMapNodeRenderer} />
              <EdgeTypeLegend />
              {/* Per-type arrow markers for dependency edges */}
              <svg>
                <defs>
                  {EDGE_TYPES.map((et) => (
                    <marker
                      key={et}
                      id={`scenario-dep-arrow-${et}`}
                      markerWidth="10"
                      markerHeight="7"
                      refX="10"
                      refY="3.5"
                      orient="auto"
                      markerUnits="userSpaceOnUse"
                    >
                      <polygon points="0 0, 10 3.5, 0 7" fill={EDGE_TYPE_STYLES[et].color} />
                    </marker>
                  ))}
                </defs>
              </svg>
            </ReactFlow>
          </Box>

          {/* Run results at bottom */}
          {activeRunId && (
            <Box sx={{ borderTop: 1, borderColor: 'divider', maxHeight: 300, overflow: 'auto' }}>
              <RunResultsPanel
                runId={activeRunId}
                onViewTranscript={() => setTranscriptOpen(true)}
              />
            </Box>
          )}
        </Box>

        {/* Side panel */}
        <Box
          sx={{
            width: 320,
            flexShrink: 0,
            borderLeft: 1,
            borderColor: 'divider',
            bgcolor: 'background.paper',
          }}
        >
          <EditorSidePanel
            content={editor.content}
            selectedActorId={editor.selectedActorId}
            selectedStepId={editor.selectedStepId}
            onUpdateActor={editor.updateActor}
            onUpdateStep={editor.updateStep}
            onRemoveStep={editor.removeStep}
            onAddAssertion={editor.addAssertion}
            onUpdateAssertion={editor.updateAssertion}
            onRemoveAssertion={editor.removeAssertion}
          />
        </Box>
      </Box>

      {/* Edge type context menu */}
      <Menu
        open={edgeContextMenu !== null}
        onClose={() => setEdgeContextMenu(null)}
        anchorReference="anchorPosition"
        anchorPosition={
          edgeContextMenu
            ? { top: edgeContextMenu.mouseY, left: edgeContextMenu.mouseX }
            : undefined
        }
      >
        {EDGE_TYPES.map((et) => (
          <MenuItem key={et} onClick={() => handleEdgeTypeSelect(et)}>
            <ListItemIcon>
              <svg width={24} height={14}>
                <line
                  x1={0}
                  y1={7}
                  x2={24}
                  y2={7}
                  stroke={EDGE_TYPE_STYLES[et].color}
                  strokeWidth={2}
                  strokeDasharray={EDGE_TYPE_STYLES[et].strokeDasharray ?? 'none'}
                />
              </svg>
            </ListItemIcon>
            <ListItemText>{EDGE_TYPE_LABELS[et]}</ListItemText>
          </MenuItem>
        ))}
      </Menu>

      <AddActorDialog
        open={addActorOpen}
        onClose={() => setAddActorOpen(false)}
        onAdd={(actor) => {
          editor.addActor(actor);
          editor.setSelectedActorId(actor.id);
          editor.setSelectedStepId(null);
        }}
      />

      <AddStepDialog
        open={addStepOpen}
        actors={editor.content.actors}
        onClose={() => setAddStepOpen(false)}
        onAdd={editor.addStep}
      />

      <TranscriptViewer
        open={transcriptOpen}
        onClose={() => setTranscriptOpen(false)}
        transcript={[]}
        actors={editor.content.actors}
      />

      <RunHistoryDialog
        open={historyOpen}
        scenarioId={scenario.id}
        onClose={() => setHistoryOpen(false)}
        onViewRun={(runId) => setActiveRunId(runId)}
      />
    </Box>
  );
}
