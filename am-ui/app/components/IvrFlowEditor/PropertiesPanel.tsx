'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';
import Button from '@mui/material/Button';
import DeleteIcon from '@mui/icons-material/Delete';
import CloseIcon from '@mui/icons-material/Close';
import type { Node } from '@xyflow/react';
import { ConfigFormSelector } from './config/ConfigFormSelector';
import type { IvrNodeType } from '../../types';

interface PropertiesPanelProps {
  node: Node | undefined;
  onUpdateNode: (nodeId: string, data: Record<string, unknown>) => void;
  onDeleteNode: (nodeId: string) => void;
  onClose: () => void;
}

export function PropertiesPanel({ node, onUpdateNode, onDeleteNode, onClose }: PropertiesPanelProps) {
  if (!node) {
    return (
      <Box
        sx={{
          width: 320,
          borderLeft: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
          p: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Typography color="text.secondary" variant="body2">
          Select a node to edit its properties
        </Typography>
      </Box>
    );
  }

  const data = node.data as Record<string, unknown>;
  const nodeType = data.type as IvrNodeType;
  const config = (data.config as Record<string, unknown>) || {};

  return (
    <Box
      sx={{
        width: 320,
        borderLeft: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper',
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="subtitle2" sx={{ flex: 1, fontWeight: 600 }}>
          Properties
        </Typography>
        <IconButton size="small" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>

      <Box sx={{ p: 1.5, flex: 1, overflow: 'auto' }}>
        <TextField
          label="Label"
          size="small"
          fullWidth
          value={(data.label as string) || ''}
          onChange={(e) => onUpdateNode(node.id, { label: e.target.value })}
          sx={{ mb: 2 }}
        />

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
          Type: {nodeType}
        </Typography>

        <Divider sx={{ mb: 2 }} />

        <ConfigFormSelector
          nodeType={nodeType}
          config={config}
          onChange={(newConfig) => onUpdateNode(node.id, { config: newConfig })}
        />
      </Box>

      <Box sx={{ p: 1.5, borderTop: 1, borderColor: 'divider' }}>
        <Button
          variant="outlined"
          color="error"
          size="small"
          fullWidth
          startIcon={<DeleteIcon />}
          onClick={() => onDeleteNode(node.id)}
        >
          Delete Node
        </Button>
      </Box>
    </Box>
  );
}
