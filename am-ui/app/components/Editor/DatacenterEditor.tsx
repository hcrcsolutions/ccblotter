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
import DialogActions from '@mui/material/DialogActions';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import type { Datacenter, DatacenterRequest } from '../../types';
import { getBackendUrl } from '../../lib/settings';

export default function DatacenterEditor() {
  const [datacenters, setDatacenters] = React.useState<Datacenter[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [editingDc, setEditingDc] = React.useState<Datacenter | null>(null);
  const [formData, setFormData] = React.useState<DatacenterRequest>({
    id: '',
    region: '',
    displayName: '',
  });
  const [saving, setSaving] = React.useState(false);

  const fetchDatacenters = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/datacenters`);
      if (!response.ok) {
        throw new Error(`Failed to fetch datacenters: ${response.status}`);
      }
      const data = await response.json();
      setDatacenters(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch datacenters');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    fetchDatacenters();
  }, [fetchDatacenters]);

  const handleOpenDialog = (dc?: Datacenter) => {
    if (dc) {
      setEditingDc(dc);
      setFormData({
        id: dc.id,
        region: dc.region,
        displayName: dc.displayName || '',
      });
    } else {
      setEditingDc(null);
      setFormData({ id: '', region: '', displayName: '' });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingDc(null);
    setFormData({ id: '', region: '', displayName: '' });
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const method = editingDc ? 'PUT' : 'POST';
      const url = editingDc
        ? `${getBackendUrl()}/api/v1/datacenters/${editingDc.id}`
        : `${getBackendUrl()}/api/v1/datacenters`;

      const response = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Failed to save datacenter: ${response.status}`);
      }

      handleCloseDialog();
      fetchDatacenters();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save datacenter');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm(`Delete datacenter "${id}"? This cannot be undone.`)) {
      return;
    }

    setError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/datacenters/${id}`, {
        method: 'DELETE',
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Failed to delete datacenter: ${response.status}`);
      }

      fetchDatacenters();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete datacenter');
    }
  };

  return (
    <Card sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">Datacenters</Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              startIcon={<RefreshIcon />}
              onClick={fetchDatacenters}
              disabled={loading}
            >
              Refresh
            </Button>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => handleOpenDialog()}
            >
              Add Datacenter
            </Button>
          </Box>
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
                  <TableCell>ID</TableCell>
                  <TableCell>Region</TableCell>
                  <TableCell>Display Name</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {datacenters.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      <Typography color="text.secondary">No datacenters configured</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  datacenters.map((dc) => (
                    <TableRow key={dc.id} hover>
                      <TableCell>{dc.id}</TableCell>
                      <TableCell>{dc.region}</TableCell>
                      <TableCell>{dc.displayName || '-'}</TableCell>
                      <TableCell>{new Date(dc.createdAt).toLocaleDateString()}</TableCell>
                      <TableCell align="right">
                        <IconButton size="small" onClick={() => handleOpenDialog(dc)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton size="small" onClick={() => handleDelete(dc.id)} color="error">
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

        <Dialog open={dialogOpen} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
          <DialogTitle>{editingDc ? 'Edit Datacenter' : 'Add Datacenter'}</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
              <TextField
                label="ID"
                value={formData.id}
                onChange={(e) => setFormData({ ...formData, id: e.target.value })}
                disabled={!!editingDc}
                required
                fullWidth
                placeholder="e.g., dc1, us-east-1"
                helperText={editingDc ? 'ID cannot be changed' : 'Unique identifier for the datacenter'}
              />
              <TextField
                label="Region"
                value={formData.region}
                onChange={(e) => setFormData({ ...formData, region: e.target.value })}
                required
                fullWidth
                placeholder="e.g., us-east, us-west, eu-west"
              />
              <TextField
                label="Display Name"
                value={formData.displayName}
                onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                fullWidth
                placeholder="e.g., US East (Virginia)"
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseDialog}>Cancel</Button>
            <Button
              variant="contained"
              onClick={handleSave}
              disabled={saving || !formData.id || !formData.region}
            >
              {saving ? <CircularProgress size={24} /> : 'Save'}
            </Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
}
