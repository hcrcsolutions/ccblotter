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
import InputAdornment from '@mui/material/InputAdornment';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import RefreshIcon from '@mui/icons-material/Refresh';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import type { NodeSummary } from '../../types';
import { getBackendUrl } from '../../lib/settings';

interface EdgeResponse {
  id: string;
  sourceId: string;
  sourceType: string;
  sourceHostname: string;
  targetId: string;
  targetType: string;
  targetHostname: string;
  bandwidthMbps: number | null;
  createdAt: string;
}

interface EdgeRequest {
  sourceId: string;
  targetId: string;
  bandwidthMbps?: number;
}

// Valid edge types: TRUNK -> SBC, SBC -> SIP, SIP -> MEDIA
const VALID_EDGE_TYPES: Record<string, string[]> = {
  TRUNK: ['SBC'],
  SBC: ['SIP'],
  SIP: ['MEDIA'],
};

export default function EdgeEditor() {
  const [edges, setEdges] = React.useState<EdgeResponse[]>([]);
  const [nodes, setNodes] = React.useState<NodeSummary[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [formData, setFormData] = React.useState<EdgeRequest>({
    sourceId: '',
    targetId: '',
  });
  const [saving, setSaving] = React.useState(false);

  // Filter state
  const [filterText, setFilterText] = React.useState('');

  // Selected source type to filter targets
  const [selectedSourceType, setSelectedSourceType] = React.useState<string>('');

  const fetchEdges = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/edges`);
      if (!response.ok) {
        throw new Error(`Failed to fetch edges: ${response.status}`);
      }
      const data = await response.json();
      setEdges(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch edges');
    } finally {
      setLoading(false);
    }
  }, []);

  const [nodesLoading, setNodesLoading] = React.useState(true);
  const [nodesError, setNodesError] = React.useState<string | null>(null);

  const fetchNodes = React.useCallback(async () => {
    setNodesLoading(true);
    setNodesError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/nodes?includeUnhealthy=true`);
      if (!response.ok) {
        throw new Error(`Failed to fetch nodes: ${response.status}`);
      }
      const data = await response.json();
      setNodes(data.nodes || []);
    } catch (err) {
      setNodesError(err instanceof Error ? err.message : 'Failed to fetch nodes');
    } finally {
      setNodesLoading(false);
    }
  }, []);

  React.useEffect(() => {
    fetchEdges();
    fetchNodes();
  }, [fetchEdges, fetchNodes]);

  const handleOpenDialog = () => {
    setFormData({ sourceId: '', targetId: '' });
    setSelectedSourceType('');
    setDialogOpen(true);
  };

  const handleCloseDialog = () => {
    setDialogOpen(false);
  };

  const handleSourceChange = (sourceId: string) => {
    const sourceNode = nodes.find(n => n.id === sourceId);
    setSelectedSourceType(sourceNode?.type || '');
    setFormData({ ...formData, sourceId, targetId: '' });
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/edges`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(formData),
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Failed to create edge: ${response.status}`);
      }

      handleCloseDialog();
      fetchEdges();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create edge');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (edgeId: string) => {
    if (!confirm('Delete this connection? This cannot be undone.')) {
      return;
    }

    setError(null);
    try {
      const response = await fetch(`${getBackendUrl()}/api/v1/edges/${encodeURIComponent(edgeId)}`, {
        method: 'DELETE',
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || `Failed to delete edge: ${response.status}`);
      }

      fetchEdges();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete edge');
    }
  };

  const getTypeColor = (type: string): 'warning' | 'info' | 'primary' | 'secondary' | 'default' => {
    switch (type) {
      case 'TRUNK': return 'warning';
      case 'SBC': return 'info';
      case 'SIP': return 'primary';
      case 'MEDIA': return 'secondary';
      default: return 'default';
    }
  };

  // Filter edges based on search text
  const filteredEdges = React.useMemo(() => {
    if (!filterText.trim()) {
      return edges;
    }
    const search = filterText.toLowerCase();
    return edges.filter(edge =>
      edge.sourceId.toLowerCase().includes(search) ||
      edge.sourceType.toLowerCase().includes(search) ||
      edge.sourceHostname.toLowerCase().includes(search) ||
      edge.targetId.toLowerCase().includes(search) ||
      edge.targetType.toLowerCase().includes(search) ||
      edge.targetHostname.toLowerCase().includes(search)
    );
  }, [edges, filterText]);

  // Get valid source nodes (TRUNK, SBC, SIP - nodes that can have outgoing edges)
  const sourceNodes = nodes.filter(n => ['TRUNK', 'SBC', 'SIP'].includes(n.type));

  // Get existing edge pairs for duplicate prevention
  const existingEdgePairs = React.useMemo(() => {
    return new Set(edges.map(e => `${e.sourceId}->${e.targetId}`));
  }, [edges]);

  // Get valid target nodes based on selected source type, excluding already connected targets
  const targetNodes = React.useMemo(() => {
    if (!selectedSourceType || !VALID_EDGE_TYPES[selectedSourceType]) {
      return [];
    }
    const validTargetTypes = VALID_EDGE_TYPES[selectedSourceType];
    return nodes
      .filter(n => validTargetTypes.includes(n.type))
      .filter(n => !existingEdgePairs.has(`${formData.sourceId}->${n.id}`));
  }, [selectedSourceType, nodes, existingEdgePairs, formData.sourceId]);

  // Count of already connected targets for the selected source
  const alreadyConnectedCount = React.useMemo(() => {
    if (!formData.sourceId || !selectedSourceType || !VALID_EDGE_TYPES[selectedSourceType]) {
      return 0;
    }
    const validTargetTypes = VALID_EDGE_TYPES[selectedSourceType];
    const totalValidTargets = nodes.filter(n => validTargetTypes.includes(n.type)).length;
    return totalValidTargets - targetNodes.length;
  }, [formData.sourceId, selectedSourceType, nodes, targetNodes]);

  return (
    <Card sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <CardContent sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Box>
            <Typography variant="h6">Connections (Edges)</Typography>
            <Typography variant="body2" color="text.secondary">
              Define how infrastructure components connect: Trunk → SBC → SIP → Media
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              startIcon={<RefreshIcon />}
              onClick={fetchEdges}
              disabled={loading}
            >
              Refresh
            </Button>
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={handleOpenDialog}
              disabled={nodesLoading || nodes.length === 0}
            >
              {nodesLoading ? 'Loading...' : 'Add Connection'}
            </Button>
          </Box>
        </Box>

        {/* Filter field */}
        <Box sx={{ mb: 2 }}>
          <TextField
            size="small"
            placeholder="Filter by source, target, type, or hostname..."
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            fullWidth
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon color="action" fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: filterText && (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setFilterText('')}>
                    <ClearIcon fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ),
            }}
          />
          {filterText && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
              Showing {filteredEdges.length} of {edges.length} connections
            </Typography>
          )}
        </Box>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        {nodesError && (
          <Alert severity="warning" sx={{ mb: 2 }} onClose={() => setNodesError(null)}>
            {nodesError} - Cannot add new connections
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
                  <TableCell>Source</TableCell>
                  <TableCell align="center" sx={{ width: 50 }}></TableCell>
                  <TableCell>Target</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredEdges.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      <Typography color="text.secondary">
                        {filterText ? 'No connections match filter' : 'No connections defined'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredEdges.map((edge) => (
                    <TableRow key={edge.id} hover>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Chip
                            label={edge.sourceType}
                            size="small"
                            color={getTypeColor(edge.sourceType)}
                          />
                          <Box>
                            <Typography variant="body2">{edge.sourceId}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {edge.sourceHostname}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell align="center">
                        <ArrowForwardIcon color="action" fontSize="small" />
                      </TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <Chip
                            label={edge.targetType}
                            size="small"
                            color={getTypeColor(edge.targetType)}
                          />
                          <Box>
                            <Typography variant="body2">{edge.targetId}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {edge.targetHostname}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell>
                        {new Date(edge.createdAt).toLocaleDateString()}
                      </TableCell>
                      <TableCell align="right">
                        <IconButton size="small" onClick={() => handleDelete(edge.id)} color="error">
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
          <DialogTitle>Add Connection</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
              <Alert severity="info" sx={{ mb: 1 }}>
                Connections follow the topology: Trunk → SBC → SIP → Media
              </Alert>
              <TextField
                label="Source Node"
                value={formData.sourceId}
                onChange={(e) => handleSourceChange(e.target.value)}
                required
                fullWidth
                select
              >
                {sourceNodes.map((node) => (
                  <MenuItem key={node.id} value={node.id}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Chip label={node.type} size="small" color={getTypeColor(node.type)} />
                      {node.id} ({node.hostname})
                    </Box>
                  </MenuItem>
                ))}
                {sourceNodes.length === 0 && (
                  <MenuItem disabled>No source nodes available</MenuItem>
                )}
              </TextField>
              <TextField
                label="Target Node"
                value={formData.targetId}
                onChange={(e) => setFormData({ ...formData, targetId: e.target.value })}
                required
                fullWidth
                select
                disabled={!selectedSourceType}
                helperText={
                  selectedSourceType
                    ? targetNodes.length === 0
                      ? `All ${VALID_EDGE_TYPES[selectedSourceType]?.join('/')} nodes are already connected`
                      : alreadyConnectedCount > 0
                        ? `${alreadyConnectedCount} already connected, ${targetNodes.length} available`
                        : `Select a ${VALID_EDGE_TYPES[selectedSourceType]?.join(' or ')} node`
                    : 'Select a source node first'
                }
              >
                {targetNodes.map((node) => (
                  <MenuItem key={node.id} value={node.id}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Chip label={node.type} size="small" color={getTypeColor(node.type)} />
                      {node.id} ({node.hostname})
                    </Box>
                  </MenuItem>
                ))}
                {targetNodes.length === 0 && selectedSourceType && (
                  <MenuItem disabled>
                    All {VALID_EDGE_TYPES[selectedSourceType]?.join('/')} nodes already connected
                  </MenuItem>
                )}
              </TextField>
              <TextField
                label="Bandwidth (Mbps)"
                type="number"
                value={formData.bandwidthMbps ?? ''}
                onChange={(e) => {
                  const val = e.target.value;
                  if (val === '') {
                    setFormData({ ...formData, bandwidthMbps: undefined });
                  } else {
                    const parsed = parseInt(val, 10);
                    if (!Number.isNaN(parsed) && parsed >= 0) {
                      setFormData({ ...formData, bandwidthMbps: parsed });
                    }
                  }
                }}
                fullWidth
                helperText="Optional: Connection bandwidth capacity"
                inputProps={{ min: 0 }}
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseDialog}>Cancel</Button>
            <Button
              variant="contained"
              onClick={handleSave}
              disabled={saving || !formData.sourceId || !formData.targetId}
            >
              {saving ? <CircularProgress size={24} /> : 'Create'}
            </Button>
          </DialogActions>
        </Dialog>
      </CardContent>
    </Card>
  );
}
