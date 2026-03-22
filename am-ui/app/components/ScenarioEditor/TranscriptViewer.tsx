'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import type { TranscriptEntry } from '../../types';
import { ROLE_COLORS } from './scenarioConstants';
import type { ScenarioActor } from '../../types';

interface TranscriptViewerProps {
  open: boolean;
  onClose: () => void;
  transcript: TranscriptEntry[];
  actors: ScenarioActor[];
}

export function TranscriptViewer({ open, onClose, transcript, actors }: TranscriptViewerProps) {
  const [filter, setFilter] = React.useState('');

  const actorRoleMap = React.useMemo(() => {
    const map: Record<string, string> = {};
    for (const a of actors) {
      map[a.id] = a.role;
    }
    return map;
  }, [actors]);

  const filtered = React.useMemo(() => {
    if (!filter) {
      return transcript;
    }
    const lower = filter.toLowerCase();
    return transcript.filter(
      (e) =>
        e.actorName.toLowerCase().includes(lower) ||
        e.action.toLowerCase().includes(lower) ||
        e.detail.toLowerCase().includes(lower)
    );
  }, [transcript, filter]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
      <DialogTitle>Execution Transcript</DialogTitle>
      <DialogContent>
        <TextField
          size="small"
          placeholder="Filter by actor, action, or detail..."
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          fullWidth
          sx={{ mb: 2 }}
        />
        <Box sx={{ maxHeight: 500, overflow: 'auto' }}>
          <Table size="small" stickyHeader>
            <TableHead>
              <TableRow>
                <TableCell>Time</TableCell>
                <TableCell>Actor</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Detail</TableCell>
                <TableCell>Duration</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map((entry, idx) => {
                const role = actorRoleMap[entry.actorId];
                const color = role ? ROLE_COLORS[role as keyof typeof ROLE_COLORS] : undefined;
                return (
                  <TableRow key={idx}>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontSize: '0.75rem' }}>
                      {new Date(entry.timestamp).toLocaleTimeString()}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={entry.actorName}
                        size="small"
                        sx={{
                          bgcolor: color,
                          color: color ? '#fff' : undefined,
                          fontSize: '0.7rem',
                          height: 20,
                        }}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontSize: '0.8rem' }}>
                        {entry.action}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={entry.status}
                        size="small"
                        color={entry.status === 'SUCCESS' ? 'success' : 'error'}
                        variant="outlined"
                        sx={{ fontSize: '0.65rem', height: 18 }}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ maxWidth: 300, display: 'block' }}>
                        {entry.detail}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.75rem' }}>
                      {entry.durationMs != null ? `${entry.durationMs}ms` : ''}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
