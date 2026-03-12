'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import LinearProgress from '@mui/material/LinearProgress';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import EditIcon from '@mui/icons-material/Edit';
import CloseIcon from '@mui/icons-material/Close';
import { getBackendUrl } from '../../lib/settings';
import type { IvrFlowSummary } from '../../types';

export default function IvrFlowsPage() {
  const router = useRouter();
  const [flows, setFlows] = React.useState<IvrFlowSummary[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [snackbar, setSnackbar] = React.useState<string | null>(null);
  const [createOpen, setCreateOpen] = React.useState(false);
  const [createName, setCreateName] = React.useState('');
  const [createDescription, setCreateDescription] = React.useState('');
  const [deleteTarget, setDeleteTarget] = React.useState<IvrFlowSummary | null>(null);

  const fetchFlows = React.useCallback(async () => {
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows`);
      if (!res.ok) {
        throw new Error('Failed to fetch flows');
      }
      const data: IvrFlowSummary[] = await res.json();
      setFlows(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to fetch flows');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    fetchFlows();
  }, [fetchFlows]);

  const handleCreate = async () => {
    if (!createName.trim()) {
      return;
    }
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: createName, description: createDescription }),
      });
      if (!res.ok) {
        throw new Error('Failed to create flow');
      }
      const created = await res.json();
      setCreateOpen(false);
      setCreateName('');
      setCreateDescription('');
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
      await fetchFlows();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete flow');
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
            <Typography variant="h6">
              IVR Flows ({flows.length})
            </Typography>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => setCreateOpen(true)}
            >
              New Flow
            </Button>
          </Box>

          {loading ? (
            <LinearProgress />
          ) : flows.length === 0 ? (
            <Typography color="text.secondary">No IVR flows yet. Create one to get started.</Typography>
          ) : (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Version</TableCell>
                    <TableCell>Updated</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {flows.map((flow) => (
                    <TableRow key={flow.id} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {flow.name}
                        </Typography>
                        {flow.description && (
                          <Typography variant="caption" color="text.secondary">
                            {flow.description}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={flow.status}
                          size="small"
                          color={flow.status === 'PUBLISHED' ? 'success' : 'default'}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell>{flow.version}</TableCell>
                      <TableCell>
                        {new Date(flow.updatedAt).toLocaleString()}
                      </TableCell>
                      <TableCell align="right">
                        <IconButton
                          size="small"
                          onClick={() => router.push(`/ivr-flows/${flow.id}`)}
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => setDeleteTarget(flow)}
                          color="error"
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
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
