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
import { FlowTesterDialog } from '../../components/IvrFlowEditor/FlowTesterDialog';
import { IvrFlowGrid } from '../../components/IvrFlowGrid/IvrFlowGrid';
import type { IvrFlowGridRef } from '../../components/IvrFlowGrid/IvrFlowGrid';
import type { IvrFlowSummary } from '../../types';

export default function IvrFlowsPage() {
  const router = useRouter();
  const gridRef = React.useRef<IvrFlowGridRef>(null);
  const [flowCount, setFlowCount] = React.useState<number>(0);
  const [error, setError] = React.useState<string | null>(null);
  const [snackbar, setSnackbar] = React.useState<string | null>(null);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [createName, setCreateName] = React.useState('');
  const [createDescription, setCreateDescription] = React.useState('');
  const [createBusinessUnit, setCreateBusinessUnit] = React.useState('');
  const [deleteTarget, setDeleteTarget] = React.useState<IvrFlowSummary | null>(null);
  const [testTarget, setTestTarget] = React.useState<IvrFlowSummary | null>(null);

  const handleCreate = async () => {
    if (!createName.trim()) {
      return;
    }
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: createName, description: createDescription, businessUnit: createBusinessUnit || null }),
      });
      if (!res.ok) {
        throw new Error('Failed to create flow');
      }
      const created = await res.json();
      setCreateOpen(false);
      setCreateName('');
      setCreateDescription('');
      setCreateBusinessUnit('');
      setSnackbar(`Created flow: ${created.name}`);
      router.push(`/ivr-flows/${created.id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create flow');
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows/${deleteTarget.id}`, {
        method: 'DELETE',
      });
      if (!res.ok) {
        throw new Error('Failed to delete flow');
      }
      setDeleteTarget(null);
      setSnackbar(`Deleted flow: ${deleteTarget.name}`);
      gridRef.current?.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete flow');
    }
  };

  const handleMetadata = React.useCallback((metadata: Record<string, unknown>) => {
    if (typeof metadata.flowCount === 'number') {
      setFlowCount(metadata.flowCount);
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
              IVR Flows ({flowCount})
            </Typography>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setCreateOpen(true)}
            >
              New Flow
            </Button>
          </Box>

          <Box sx={{ flex: 1, minHeight: 300 }}>
            <IvrFlowGrid
              ref={gridRef}
              onTest={(flow) => setTestTarget(flow)}
              onEdit={(flowId) => router.push(`/ivr-flows/${flowId}`)}
              onDelete={(flow) => setDeleteTarget(flow)}
              onMetadata={handleMetadata}
            />
          </Box>
        </CardContent>
      </Card>

      {/* Create Dialog */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create IVR Flow</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            margin="dense"
            label="Flow Name"
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
          <TextField
            margin="dense"
            label="Business Unit (optional)"
            fullWidth
            value={createBusinessUnit}
            onChange={(e) => setCreateBusinessUnit(e.target.value)}
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
        <DialogTitle>Delete Flow</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ mb: 1 }}>
            Are you sure you want to delete this flow?
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

      <FlowTesterDialog flow={testTarget} onClose={() => setTestTarget(null)} />

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
