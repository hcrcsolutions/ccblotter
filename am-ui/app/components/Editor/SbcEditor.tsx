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
import MenuItem from '@mui/material/MenuItem';
import Chip from '@mui/material/Chip';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import type { NodeSummary, NodeRegistrationRequest, Datacenter } from '../../types';

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export default function SbcEditor() {
  const [sbcs, setSbcs] = React.useState<NodeSummary[]>([]);
  const [datacenters, setDatacenters] = React.useState<Datacenter[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [editingSbc, setEditingSbc] = React.useState<NodeSummary | null>(null);
  const [formData, setFormData] = React.useState<NodeRegistrationRequest>({
    id: '',
    type: 'SBC',
    hostname: '',
    ipAddress: '',
    datacenter: '',
    maxSessions: 5000,
  });
  const [saving, setSaving] = React.useState(false);

  const fetchSbcs = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE}/api/v1/nodes?type=SBC&includeUnhealthy=true`);
      if (!response.ok) {
        throw new Error(`Failed to fetch SBCs: ${response.status}`);
      }
      const data = await response.json();
      setSbcs(data.nodes || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch SBCs');
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchDatacenters = React.useCallback(async () => {
    try {
      const response = await fetch(`${API_BASE}/api/v1/datacenters`);
      if (response.ok) {
        const data = await response.json();
        setDatacenters(data);
      }
    } catch {
      // Ignore datacenter fetch errors
    }
  }, []);

  React.useEffect(() => {
    fetchSbcs();
    fetchDatacenters();
  }, [fetchSbcs, fetchDatacenters]);

  const handleOpenDialog = (sbc?: NodeSummary) => {
    if (sbc) {
      setEditingSbc(sbc);
      setFormData({
        id: sbc.id,
        type: 'SBC',
        hostname: sbc.hostname,
        ipAddress: sbc.ipAddress,
        datacenter: sbc.datacenter,
        maxSessions: sbc.maxSessions,
      });
    } else {
      setEditingSbc(null);
      setFormData({
        id: '',
        type: 'SBC',
        hostname: '',
        ipAddress: '',
        datacenter: datacenters[0]?.id || '',
        maxSessions: 5000,
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingSbc(null);
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE}/api/v1/nodes/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Failed to save SBC: ${response.status}`);
      }

      handleCloseDialog();
      fetchSbcs();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save SBC');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm(`Delete SBC "${id}"? This cannot be undone.`)) {
      return;
    }

    setError(null);
    try {
      const response = await fetch(`${API_BASE}/api/v1/nodes/${id}`, {
        method: 'DELETE',
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Failed to delete SBC: ${response.status}`);
      }

      fetchSbcs();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete SBC');
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'HEALTHY': return 'success';
      case 'DEGRADED': return 'warning';
      case 'UNHEALTHY': return 'error';
      default: return 'default';
    }
  };

  return (
    <Card sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">Session Border Controllers (SBCs)</Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              startIcon={<RefreshIcon />}
              onClick={fetchSbcs}
              disabled={loading}
            >
              Refresh
            </Button>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => handleOpenDialog()}
            >
              Add SBC
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
                  <TableCell>Hostname</TableCell>
                  <TableCell>IP Address</TableCell>
                  <TableCell>Datacenter</TableCell>
                  <TableCell>Max Sessions</TableCell>
                  <TableCell>Active</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {sbcs.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} align="center">
                      <Typography color="text.secondary">No SBCs configured</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  sbcs.map((sbc) => (
                    <TableRow key={sbc.id} hover>
                      <TableCell>{sbc.id}</TableCell>
                      <TableCell>{sbc.hostname}</TableCell>
                      <TableCell>{sbc.ipAddress}</TableCell>
                      <TableCell>{sbc.datacenter}</TableCell>
                      <TableCell>{sbc.maxSessions}</TableCell>
                      <TableCell>{sbc.activeSessions}</TableCell>
                      <TableCell>
                        <Chip
                          label={sbc.status}
                          size="small"
                          color={getStatusColor(sbc.status) as 'success' | 'warning' | 'error' | 'default'}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <IconButton size="small" onClick={() => handleOpenDialog(sbc)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton size="small" onClick={() => handleDelete(sbc.id)} color="error">
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
          <DialogTitle>{editingSbc ? 'Edit SBC' : 'Add SBC'}</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
              <TextField
                label="ID"
                value={formData.id}
                onChange={(e) => setFormData({ ...formData, id: e.target.value })}
                disabled={!!editingSbc}
                required
                fullWidth
                placeholder="e.g., sbc-1"
                helperText={editingSbc ? 'ID cannot be changed' : 'Unique identifier for the SBC'}
              />
              <TextField
                label="Hostname"
                value={formData.hostname}
                onChange={(e) => setFormData({ ...formData, hostname: e.target.value })}
                required
                fullWidth
                placeholder="e.g., sbc-01.dc1.example.com"
              />
              <TextField
                label="IP Address"
                value={formData.ipAddress}
                onChange={(e) => setFormData({ ...formData, ipAddress: e.target.value })}
                required
                fullWidth
                placeholder="e.g., 10.1.1.10"
              />
              <TextField
                label="Datacenter"
                value={formData.datacenter}
                onChange={(e) => setFormData({ ...formData, datacenter: e.target.value })}
                required
                fullWidth
                select
              >
                {datacenters.map((dc) => (
                  <MenuItem key={dc.id} value={dc.id}>
                    {dc.displayName || dc.id} ({dc.region})
                  </MenuItem>
                ))}
                {datacenters.length === 0 && (
                  <MenuItem disabled>No datacenters available</MenuItem>
                )}
              </TextField>
              <TextField
                label="Max Sessions"
                type="number"
                value={formData.maxSessions}
                onChange={(e) => setFormData({ ...formData, maxSessions: parseInt(e.target.value) || 0 })}
                required
                fullWidth
                helperText="Maximum concurrent sessions this SBC can handle"
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseDialog}>Cancel</Button>
            <Button
              variant="contained"
              onClick={handleSave}
              disabled={saving || !formData.id || !formData.hostname || !formData.ipAddress || !formData.datacenter}
            >
              {saving ? <CircularProgress size={24} /> : 'Save'}
            </Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
}
