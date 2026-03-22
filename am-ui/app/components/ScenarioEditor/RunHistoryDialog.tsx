'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import CircularProgress from '@mui/material/CircularProgress';
import { getBackendUrl } from '../../lib/settings';
import type { ScenarioRun } from '../../types';

interface RunHistoryDialogProps {
  open: boolean;
  scenarioId: string;
  onClose: () => void;
  onViewRun: (runId: string) => void;
}

export function RunHistoryDialog({ open, scenarioId, onClose, onViewRun }: RunHistoryDialogProps) {
  const [runs, setRuns] = React.useState<ScenarioRun[]>([]);
  const [loading, setLoading] = React.useState(false);

  React.useEffect(() => {
    if (!open) {
      return;
    }
    setLoading(true);
    fetch(`${getBackendUrl()}/api/v1/test-scenarios/${scenarioId}/runs`)
      .then((res) => res.json())
      .then((data) => setRuns(data))
      .catch(() => setRuns([]))
      .finally(() => setLoading(false));
  }, [open, scenarioId]);

  const resultColorMap: Record<string, 'success' | 'error' | 'warning'> = {
    PASS: 'success',
    FAIL: 'error',
    ERROR: 'warning',
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Run History</DialogTitle>
      <DialogContent>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
            <CircularProgress />
          </Box>
        ) : runs.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
            No runs yet
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Result</TableCell>
                <TableCell>Timing</TableCell>
                <TableCell>Version</TableCell>
                <TableCell>Duration</TableCell>
                <TableCell></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {runs.map((run) => {
                const duration = run.startedAt && run.finishedAt
                  ? Math.round((new Date(run.finishedAt).getTime() - new Date(run.startedAt).getTime()) / 1000)
                  : null;
                return (
                  <TableRow key={run.id} hover>
                    <TableCell sx={{ fontSize: '0.8rem' }}>
                      {new Date(run.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <Chip label={run.status} size="small" variant="outlined" sx={{ fontSize: '0.7rem' }} />
                    </TableCell>
                    <TableCell>
                      {run.result && (
                        <Chip
                          label={run.result}
                          size="small"
                          color={resultColorMap[run.result]}
                          variant="outlined"
                          sx={{ fontSize: '0.7rem' }}
                        />
                      )}
                    </TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>{run.timingMode}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>v{run.scenarioVersion}</TableCell>
                    <TableCell sx={{ fontSize: '0.8rem' }}>
                      {duration != null ? `${duration}s` : '-'}
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        onClick={() => { onViewRun(run.id); onClose(); }}
                      >
                        View
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
