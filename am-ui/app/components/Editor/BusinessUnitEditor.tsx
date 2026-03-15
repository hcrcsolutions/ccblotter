'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import { getBackendUrl } from '../../lib/settings';

export default function BusinessUnitEditor() {
  const [businessUnits, setBusinessUnits] = React.useState<string[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [newName, setNewName] = React.useState('');
  const [adding, setAdding] = React.useState(false);
  const [deleteTarget, setDeleteTarget] = React.useState<string | null>(null);

  const fetchBusinessUnits = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/business-units`);
      if (!response.ok) {
        throw new Error(`Failed to fetch business units: ${response.status}`);
      }
      const data: string[] = await response.json();
      setBusinessUnits(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch business units');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    fetchBusinessUnits();
  }, [fetchBusinessUnits]);

  const handleAdd = async () => {
    const trimmed = newName.trim();
    if (!trimmed) {
      return;
    }

    setAdding(true);
    setError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/business-units`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: trimmed }),
      });

      if (response.status === 409) {
        setError(`"${trimmed}" already exists`);
        return;
      }

      if (!response.ok) {
        throw new Error(`Failed to add business unit: ${response.status}`);
      }

      setNewName('');
      fetchBusinessUnits();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add business unit');
    } finally {
      setAdding(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }

    setError(null);
    try {
      const response = await fetch(
        `${getBackendUrl()}/api/v1/business-units/${encodeURIComponent(deleteTarget)}`,
        { method: 'DELETE' },
      );

      if (!response.ok) {
        throw new Error(`Failed to delete business unit: ${response.status}`);
      }

      fetchBusinessUnits();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete business unit');
    } finally {
      setDeleteTarget(null);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleAdd();
    }
  };

  return (
    <Card sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">Business Units</Typography>
          <Button startIcon={<RefreshIcon />} onClick={fetchBusinessUnits} disabled={loading}>
            Refresh
          </Button>
        </Box>

        <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
          <TextField
            label="New Business Unit"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="e.g., Wealth Management"
            size="small"
            sx={{ width: 300 }}
          />
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={handleAdd}
            disabled={adding || !newName.trim()}
          >
            Add
          </Button>
        </Box>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flex: 1 }}>
            <CircularProgress />
          </Box>
        ) : (
          <TableContainer sx={{ flex: 1 }}>
            <Table stickyHeader size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {businessUnits.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={2} align="center">
                      <Typography color="text.secondary">No business units defined</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  businessUnits.map((name) => (
                    <TableRow key={name} hover>
                      <TableCell>{name}</TableCell>
                      <TableCell align="right">
                        <IconButton size="small" onClick={() => setDeleteTarget(name)} color="error">
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        <Dialog open={deleteTarget !== null} onClose={() => setDeleteTarget(null)}>
          <DialogTitle>Delete Business Unit</DialogTitle>
          <DialogContent>
            <DialogContentText>
              Are you sure you want to delete &quot;{deleteTarget}&quot;? This will not affect existing flows that use
              this business unit.
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
            <Button onClick={handleDelete} color="error">
              Delete
            </Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
}
