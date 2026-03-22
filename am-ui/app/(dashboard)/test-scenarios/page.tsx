'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import AddIcon from '@mui/icons-material/Add';
import CloseIcon from '@mui/icons-material/Close';
import { getBackendUrl } from '../../lib/settings';
import { ScenarioGrid } from '../../components/ScenarioGrid/ScenarioGrid';
import type { ScenarioGridRef } from '../../components/ScenarioGrid/ScenarioGrid';
import type { ScenarioSummary } from '../../types';

export default function TestScenariosPage() {
  const router = useRouter();
  const gridRef = React.useRef<ScenarioGridRef>(null);
  const [scenarioCount, setScenarioCount] = React.useState<number>(0);
  const [error, setError] = React.useState<string | null>(null);
  const [snackbar, setSnackbar] = React.useState<string | null>(null);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [createName, setCreateName] = React.useState('');
  const [createDescription, setCreateDescription] = React.useState('');
  const [deleteTarget, setDeleteTarget] = React.useState<ScenarioSummary | null>(null);

  const handleCreate = async () => {
    if (!createName.trim()) {
      return;
    }
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/test-scenarios`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: createName, description: createDescription || null }),
      });
      if (!res.ok) {
        throw new Error('Failed to create scenario');
      }
      const created = await res.json();
      setCreateOpen(false);
      setCreateName('');
      setCreateDescription('');
      setSnackbar(`Created scenario: ${created.name}`);
      router.push(`/test-scenarios/${created.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create scenario');
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/test-scenarios/${deleteTarget.id}`, {
        method: 'DELETE',
      });
      if (!res.ok) {
        throw new Error('Failed to delete scenario');
      }
      setDeleteTarget(null);
      setSnackbar(`Deleted scenario: ${deleteTarget.name}`);
      gridRef.current?.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete scenario');
    }
  };

  const handleMetadata = React.useCallback((metadata: Record<string, unknown>) => {
    if (typeof metadata.scenarioCount === 'number') {
      setScenarioCount(metadata.scenarioCount);
    }
  }, []);

  return (
    <Box sx={{ p: 3, height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card sx={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', '&:last-child': { pb: 2 } }}>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="h6">
              Test Scenarios ({scenarioCount})
            </Typography>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setCreateOpen(true)}
            >
              New Scenario
            </Button>
          </Box>

          <Box sx={{ flex: 1, minHeight: 300 }}>
            <ScenarioGrid
              ref={gridRef}
              onEdit={(scenarioId) => router.push(`/test-scenarios/${scenarioId}`)}
              onDelete={(scenario) => setDeleteTarget(scenario)}
              onMetadata={handleMetadata}
            />
          </Box>
        </CardContent>
      </Card>

      {/* Create Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Test Scenario</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Scenario Name"
            fullWidth
            value={createName}
            onChange={(e) => setCreateName(e.target.value)}
            sx={{ mb: 2 }}
          />
          <TextField
            margin="dense"
            label="Description (optional)"
            fullWidth
            multiline
            value={createDescription}
            onChange={(e) => setCreateDescription(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{
              '& .MuiInputBase-root': {
                minHeight: '100px',
                alignItems: 'flex-start',
              },
            }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button onClick={handleCreate} variant="contained" disabled={!createName.trim()}>
            Create
          </Button>
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>Delete Scenario</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ mb: 1 }}>
            Are you sure you want to delete this scenario?
          </Typography>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {deleteTarget?.name}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button onClick={handleDelete} color="error" variant="contained">
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
        action={
          <IconButton size="small" color="inherit" onClick={() => setSnackbar(null)}>
            <CloseIcon fontSize="small" />
          </IconButton>
        }
      />
    </Box>
  );
}
