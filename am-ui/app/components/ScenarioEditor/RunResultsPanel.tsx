'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import { getBackendUrl } from '../../lib/settings';
import type { ScenarioRun, AssertionResult } from '../../types';
import { ASSERTION_TYPE_LABELS } from './scenarioConstants';

interface RunResultsPanelProps {
  runId: string | null;
  onViewTranscript: () => void;
}

export function RunResultsPanel({ runId, onViewTranscript }: RunResultsPanelProps) {
  const [run, setRun] = React.useState<ScenarioRun | null>(null);
  const [loading, setLoading] = React.useState(false);
  const pollRef = React.useRef<ReturnType<typeof setInterval> | null>(null);

  React.useEffect(() => {
    if (!runId) {
      setRun(null);
      return;
    }

    setLoading(true);
    const poll = async () => {
      try {
        const res = await fetch(`${getBackendUrl()}/api/v1/test-scenarios/runs/${runId}`);
        if (res.ok) {
          const data = await res.json();
          setRun(data);
          setLoading(false);
          if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'CANCELLED') {
            if (pollRef.current) {
              clearInterval(pollRef.current);
              pollRef.current = null;
            }
          }
        }
      } catch {
        // continue polling
      }
    };

    poll();
    pollRef.current = setInterval(poll, 1000);

    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, [runId]);

  if (!runId) {
    return null;
  }

  if (loading && !run) {
    return (
      <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
        <CircularProgress size={20} />
        <Typography variant="body2">Starting execution...</Typography>
      </Box>
    );
  }

  if (!run) {
    return null;
  }

  const isRunning = run.status === 'PENDING' || run.status === 'RUNNING';
  const assertionResults: AssertionResult[] = run.assertionResults || [];

  const resultColorMap: Record<string, 'success' | 'error' | 'warning'> = {
    PASS: 'success',
    FAIL: 'error',
    ERROR: 'warning',
  };

  return (
    <Box sx={{ p: 2 }}>
      {/* Status banner */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        {isRunning && <CircularProgress size={20} />}
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Run Status:
        </Typography>
        <Chip
          label={run.status}
          size="small"
          color={run.result ? resultColorMap[run.result] : 'default'}
          variant="filled"
        />
        {run.result && (
          <Chip
            label={run.result}
            size="small"
            color={resultColorMap[run.result]}
            variant="outlined"
          />
        )}
      </Box>

      {run.errorMessage && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {run.errorMessage}
        </Alert>
      )}

      {/* Assertion results table */}
      {assertionResults.length > 0 && (
        <>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Assertion Results
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Status</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Expected</TableCell>
                <TableCell>Actual</TableCell>
                <TableCell>Message</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {assertionResults.map((ar, idx) => (
                <TableRow key={idx}>
                  <TableCell>
                    {ar.passed
                      ? <CheckCircleIcon color="success" sx={{ fontSize: 18 }} />
                      : <CancelIcon color="error" sx={{ fontSize: 18 }} />
                    }
                  </TableCell>
                  <TableCell>{ASSERTION_TYPE_LABELS[ar.type] || ar.type}</TableCell>
                  <TableCell>{ar.expected}</TableCell>
                  <TableCell>{ar.actual}</TableCell>
                  <TableCell>{ar.message}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </>
      )}

      {/* View transcript link */}
      {run.transcript && run.transcript.length > 0 && (
        <Typography
          variant="body2"
          color="primary"
          sx={{ mt: 2, cursor: 'pointer', textDecoration: 'underline' }}
          onClick={onViewTranscript}
        >
          View Full Transcript ({run.transcript.length} entries)
        </Typography>
      )}
    </Box>
  );
}
