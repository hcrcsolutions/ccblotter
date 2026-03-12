'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type NodeTypes,
  type EdgeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { useFlowEditor } from './useFlowEditor';
import { FlowToolbar } from './FlowToolbar';
import { NodePalette } from './NodePalette';
import { PropertiesPanel } from './PropertiesPanel';
import { ValidationDialog } from './ValidationDialog';
import { IvrNode } from './nodes';
import { FlowEdge } from './FlowEdge';
import type { IvrNodeType } from '../../types';

const nodeTypes: NodeTypes = {
  ivrNode: IvrNode,
};

const edgeTypes: EdgeTypes = {
  ivrEdge: FlowEdge,
};

interface IvrFlowEditorProps {
  flowId: string;
  flowName: string;
  flowStatus: string;
}

export function IvrFlowEditor({ flowId, flowName, flowStatus }: IvrFlowEditorProps) {
  const editor = useFlowEditor(flowId);
  const reactFlowWrapper = React.useRef<HTMLDivElement>(null);

  const selectedNode = editor.nodes.find((n) => n.id === editor.selectedNodeId);

  const handleAddNode = React.useCallback(
    (type: IvrNodeType) => {
      // Place new nodes in the center of the visible canvas area
      const position = { x: 250 + Math.random() * 200, y: 100 + Math.random() * 200 };
      editor.addNode(type, position);
    },
    [editor]
  );

  const handleNodeClick = React.useCallback(
    (_: React.MouseEvent, node: { id: string }) => {
      editor.selectNode(node.id);
    },
    [editor]
  );

  const handlePaneClick = React.useCallback(() => {
    editor.selectNode(null);
  }, [editor]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <FlowToolbar
        flowName={flowName}
        flowStatus={flowStatus}
        saving={editor.saving}
        publishing={editor.publishing}
        dirty={editor.dirty}
        onSave={editor.save}
        onPublish={editor.publish}
      />

      {editor.error && (
        <Alert severity="error" onClose={editor.clearError} sx={{ borderRadius: 0 }}>
          {editor.error}
        </Alert>
      )}

      <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <NodePalette onAddNode={handleAddNode} />

        <Box ref={reactFlowWrapper} sx={{ flex: 1 }}>
          <ReactFlow
            nodes={editor.nodes}
            edges={editor.edges}
            onNodesChange={editor.onNodesChange}
            onEdgesChange={editor.onEdgesChange}
            onConnect={editor.onConnect}
            onReconnect={editor.onReconnect}
            onNodeClick={handleNodeClick}
            onPaneClick={handlePaneClick}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            fitView
            proOptions={{ hideAttribution: true }}
            edgesReconnectable
            deleteKeyCode={['Backspace', 'Delete']}
            defaultEdgeOptions={{
              type: 'ivrEdge',
              animated: true,
            }}
          >
            <Background />
            <Controls />
            <MiniMap />
          </ReactFlow>
        </Box>

        {editor.selectedNodeId && (
          <PropertiesPanel
            node={selectedNode}
            onUpdateNode={editor.updateNodeData}
            onDeleteNode={editor.deleteNode}
            onClose={() => editor.selectNode(null)}
          />
        )}
      </Box>

      <ValidationDialog
        result={editor.publishResult}
        onClose={editor.clearPublishResult}
      />

      <Snackbar
        open={editor.snackbar !== null}
        autoHideDuration={4000}
        onClose={editor.clearSnackbar}
        message={editor.snackbar}
        action={
          <IconButton size="small" color="inherit" onClick={editor.clearSnackbar}>
            <CloseIcon fontSize="small" />
          </IconButton>
        }
      />
    </Box>
  );
}
