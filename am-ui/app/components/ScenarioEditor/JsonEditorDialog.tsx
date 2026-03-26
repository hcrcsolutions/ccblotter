'use client';

import * as React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Editor from '@monaco-editor/react';
import type { ScenarioContent } from '../../types';

function isValidScenarioContent(value: unknown): value is ScenarioContent {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const obj = value as Record<string, unknown>;
  return (
    Array.isArray(obj.actors)
    && Array.isArray(obj.steps)
    && Array.isArray(obj.assertions)
    && typeof obj.settings === 'object'
    && obj.settings !== null
  );
}

function serializeContent(content: ScenarioContent): string {
  const exported = {
    actors: content.actors,
    steps: content.steps.map(({ position: _, ...rest }) => rest),
    assertions: content.assertions,
    settings: content.settings,
  };
  return JSON.stringify(exported, null, 2);
}

interface JsonEditorDialogProps {
  open: boolean;
  initialContent: ScenarioContent;
  scenarioName: string;
  onClose: () => void;
  onApply: (content: ScenarioContent) => void;
}

export function JsonEditorDialog({ open, initialContent, scenarioName, onClose, onApply }: JsonEditorDialogProps) {
  const [jsonText, setJsonText] = React.useState('');
  const [parseError, setParseError] = React.useState<string | null>(null);
  const [valid, setValid] = React.useState(false);
  const [dragging, setDragging] = React.useState(false);
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  // Populate editor with current scenario content when dialog opens
  React.useEffect(() => {
    if (open) {
      const text = serializeContent(initialContent);
      setJsonText(text);
      setParseError(null);
      setValid(true);
    }
  }, [open, initialContent]);

  const validate = React.useCallback((text: string) => {
    if (!text.trim()) {
      setParseError(null);
      setValid(false);
      return;
    }
    try {
      const parsed = JSON.parse(text);
      if (isValidScenarioContent(parsed)) {
        setParseError(null);
        setValid(true);
      } else {
        setParseError('JSON must have top-level keys: actors, steps, assertions, settings');
        setValid(false);
      }
    } catch {
      setParseError('Invalid JSON syntax');
      setValid(false);
    }
  }, []);

  const handleEditorChange = React.useCallback(
    (value: string | undefined) => {
      const text = value ?? '';
      setJsonText(text);
      validate(text);
    },
    [validate],
  );

  const handleLoadFile = React.useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const loadFileContent = React.useCallback(
    (file: File) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        const text = e.target?.result as string;
        setJsonText(text);
        validate(text);
      };
      reader.readAsText(file);
    },
    [validate],
  );

  const handleFileChange = React.useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      if (!file) {
        return;
      }
      loadFileContent(file);
      // Reset so the same file can be loaded again
      event.target.value = '';
    },
    [loadFileContent],
  );

  const handleDownload = React.useCallback(() => {
    const blob = new Blob([jsonText], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${scenarioName.replace(/\s+/g, '-').toLowerCase()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }, [jsonText, scenarioName]);

  const handleDragOver = React.useCallback((event: React.DragEvent) => {
    event.preventDefault();
    setDragging(true);
  }, []);

  const handleDragLeave = React.useCallback(() => {
    setDragging(false);
  }, []);

  const handleDrop = React.useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      setDragging(false);
      const file = event.dataTransfer.files[0];
      if (file && file.name.endsWith('.json')) {
        loadFileContent(file);
      }
    },
    [loadFileContent],
  );

  const handleApply = React.useCallback(() => {
    try {
      const parsed = JSON.parse(jsonText) as ScenarioContent;
      onApply(parsed);
    } catch {
      // Should not happen since Apply is only enabled when valid
    }
  }, [jsonText, onApply]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Scenario JSON</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 1, pt: 1 }}>
        {parseError && (
          <Alert severity="error" sx={{ mb: 1 }}>
            {parseError}
          </Alert>
        )}
        <Box
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          sx={{
            flex: 1,
            minHeight: 400,
            border: 2,
            borderColor: dragging ? 'primary.main' : 'divider',
            borderStyle: dragging ? 'dashed' : 'solid',
            borderRadius: 1,
            transition: 'border-color 0.2s',
          }}
        >
          <Editor
            height="400px"
            language="json"
            value={jsonText}
            onChange={handleEditorChange}
            options={{
              minimap: { enabled: false },
              lineNumbers: 'on',
              wordWrap: 'on',
              scrollBeyondLastLine: false,
              fontSize: 13,
            }}
          />
        </Box>
        <input
          ref={fileInputRef}
          type="file"
          accept=".json"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleLoadFile}>Load File</Button>
        <Button onClick={handleDownload}>Save As</Button>
        <Box sx={{ flex: 1 }} />
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleApply} disabled={!valid}>
          Apply
        </Button>
      </DialogActions>
    </Dialog>
  );
}
