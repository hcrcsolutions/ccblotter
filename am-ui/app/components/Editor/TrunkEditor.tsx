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

const TRUNK_GROUPS = ['primary', 'backup', 'overflow'];

export default function TrunkEditor() {
  const [trunks, setTrunks] = React.useState<NodeSummary[]>([]);
  const [datacenters, setDatacenters] = React.useState<Datacenter[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [editingTrunk, setEditingTrunk] = React.useState<NodeSummary | null>(null);
  const [formData, setFormData] = React.useState<NodeRegistrationRequest>({
    id: '',
    type: 'TRUNK',
    hostname: '',
    ipAddress: '',
    datacenter: '',
    maxSessions: 1000,
    carrierName: '',
    trunkGroup: 'primary',
  });
  const [saving, setSaving] = React.useState(false);

  const fetchTrunks = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE}/api/v1/nodes?type=TRUNK&includeUnhealthy=true`);
      if (!response.ok) {
        throw new Error(`Failed to fetch trunks: ${response.status}`);
      }
      const data = await response.json();
      setTrunks(data.nodes || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch trunks');
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
    fetchTrunks();
    fetchDatacenters();
  }, [fetchTrunks, fetchDatacenters]);

  const handleOpenDialog = (trunk?: NodeSummary) => {
    if (trunk) {
      setEditingTrunk(trunk);
      setFormData({
        id: trunk.id,
        type: 'TRUNK',
        hostname: trunk.hostname,
        ipAddress: trunk.ipAddress,
        datacenter: trunk.datacenter,
        maxSessions: trunk.maxSessions,
        carrierName: '',
        trunkGroup: 'primary',
      });
    } else {
      setEditingTrunk(null);
      setFormData({
        id: '',
        type: 'TRUNK',
        hostname: '',
        ipAddress: '',
        datacenter: datacenters[0]?.id || '',
        maxSessions: 1000,
        carrierName: '',
        trunkGroup: 'primary',
      });
    }
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
    setEditingTrunk(null);
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
        throw new Error(errData.message || `Failed to save trunk: ${response.status}`);
      }

      handleCloseDialog();
      fetchTrunks();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save trunk');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm(`Delete trunk "${id}"? This cannot be undone.`)) {
      return;
    }

    setError(null);
    try {
      const response = await fetch(`${API_BASE}/api/v1/nodes/${id}`, {
        method: 'DELETE',
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Failed to delete trunk: ${response.status}`);
      }

      fetchTrunks();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete trunk');
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
          <Typography variant="h6">Trunks (Carrier Connections)</Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              startIcon={<RefreshIcon />}
              onClick={fetchTrunks}
              disabled={loading}
            >
              Refresh
            </Button>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => handleOpenDialog()}
            >
              Add Trunk
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
                  <TableCell>Status</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {trunks.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} align="center">
                      <Typography color="text.secondary">No trunks configured</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  trunks.map((trunk) => (
                    <TableRow key={trunk.id} hover>
                      <TableCell>{trunk.id}</TableCell>
                      <TableCell>{trunk.hostname}</TableCell>
                      <TableCell>{trunk.ipAddress}</TableCell>
                      <TableCell>{trunk.datacenter}</TableCell>
                      <TableCell>{trunk.maxSessions}</TableCell>
                      <TableCell>
                        <Chip
                          label={trunk.status}
                          size="small"
                          color={getStatusColor(trunk.status) as 'success' | 'warning' | 'error' | 'default'}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <IconButton size="small" onClick={() => handleOpenDialog(trunk)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton size="small" onClick={() => handleDelete(trunk.id)} color="error">
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
          <DialogTitle>{editingTrunk ? 'Edit Trunk' : 'Add Trunk'}</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
              <TextField
                label="ID"
                value={formData.id}
                onChange={(e) => setFormData({ ...formData, id: e.target.value })}
                disabled={!!editingTrunk}
                required
                fullWidth
                placeholder="e.g., trunk-carrier-a-1"
                helperText={editingTrunk ? 'ID cannot be changed' : 'Unique identifier for the trunk'}
              />
              <TextField
                label="Hostname"
                value={formData.hostname}
                onChange={(e) => setFormData({ ...formData, hostname: e.target.value })}
                required
                fullWidth
                placeholder="e.g., trunk-01.carrier-a.example.com"
              />
              <TextField
                label="IP Address"
                value={formData.ipAddress}
                onChange={(e) => setFormData({ ...formData, ipAddress: e.target.value })}
                required
                fullWidth
                placeholder="e.g., 10.0.1.10"
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
                label="Carrier Name"
                value={formData.carrierName}
                onChange={(e) => setFormData({ ...formData, carrierName: e.target.value })}
                fullWidth
                placeholder="e.g., AT&T, Verizon"
              />
              <TextField
                label="Trunk Group"
                value={formData.trunkGroup}
                onChange={(e) => setFormData({ ...formData, trunkGroup: e.target.value })}
                fullWidth
                select
              >
                {TRUNK_GROUPS.map((group) => (
                  <MenuItem key={group} value={group}>
                    {group.charAt(0).toUpperCase() + group.slice(1)}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                label="Max Sessions"
                type="number"
                value={formData.maxSessions}
                onChange={(e) => setFormData({ ...formData, maxSessions: parseInt(e.target.value) || 0 })}
                required
                fullWidth
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
