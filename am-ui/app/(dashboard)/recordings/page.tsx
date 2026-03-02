'use client';

import * as React from 'react';
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
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import CloseIcon from '@mui/icons-material/Close';
import DeleteIcon from '@mui/icons-material/Delete';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { useAudioPlayback } from '../../hooks/useAudioPlayback';
import { formatDuration } from '../../lib/formatters';
import { getBackendUrl } from '../../lib/settings';
import type { Recording } from '../../types';

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function RecordingsPage() {
  const [recordings, setRecordings] = React.useState<Recording[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [uploading, setUploading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [snackbar, setSnackbar] = React.useState<string | null>(null);
  const [dragOver, setDragOver] = React.useState(false);
  const [deleteTarget, setDeleteTarget] = React.useState<Recording | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const { playingStepId, play, stop } = useAudioPlayback();

  const fetchRecordings = React.useCallback(async () => {
    try {
      const res = await fetch(`${getBackendUrl()}/api/recordings`);
      if (!res.ok) throw new Error('Failed to fetch recordings');
      const data: Recording[] = await res.json();
      setRecordings(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to fetch recordings');
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    fetchRecordings();
  }, [fetchRecordings]);

  const uploadFile = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.wav')) {
      setError('Only .wav files are accepted');
      return;
    }

    setUploading(true);
    setError(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await fetch(`${getBackendUrl()}/api/recordings`, {
        method: 'POST',
        body: formData,
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || `Upload failed (${res.status})`);
      }
      setSnackbar(`Uploaded ${file.name}`);
      await fetchRecordings();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      uploadFile(file);
    }
    // Reset so same file can be selected again
    e.target.value = '';
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file) {
      uploadFile(file);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    const { filename } = deleteTarget;
    setDeleteTarget(null);
    try {
      stop();
      const res = await fetch(`${getBackendUrl()}/api/recordings/${encodeURIComponent(filename)}`, {
        method: 'DELETE',
      });
      if (!res.ok) throw new Error('Delete failed');
      setSnackbar(`Deleted ${filename}`);
      await fetchRecordings();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Delete failed');
    }
  };

  const handlePlayToggle = (filename: string) => {
    const url = `${getBackendUrl()}/api/recordings/${encodeURIComponent(filename)}`;
    play(filename, url);
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Upload Drop Zone */}
      <Box
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        sx={{
          mb: 2,
          border: '2px dashed',
          borderColor: dragOver ? 'primary.main' : 'divider',
          borderRadius: 2,
          py: 1.5,
          px: 3,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          bgcolor: dragOver ? 'action.hover' : 'transparent',
          transition: 'all 0.2s',
          cursor: 'pointer',
        }}
      >
        <UploadFileIcon sx={{ color: 'text.secondary' }} />
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          Drop a .wav file here or click to upload
        </Typography>
        <input
          ref={fileInputRef}
          type="file"
          accept=".wav"
          onChange={handleFileSelect}
          style={{ display: 'none' }}
        />
      </Box>

      {uploading && <LinearProgress sx={{ mb: 2 }} />}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Recordings Table */}
      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            Recordings ({recordings.length})
          </Typography>

          {loading ? (
            <LinearProgress />
          ) : recordings.length === 0 ? (
            <Typography color="text.secondary">No recordings uploaded yet.</Typography>
          ) : (
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Filename</TableCell>
                    <TableCell>Duration</TableCell>
                    <TableCell>Size</TableCell>
                    <TableCell>Sample Rate</TableCell>
                    <TableCell>Uploaded</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {recordings.map((rec) => (
                    <TableRow key={rec.filename} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                          {rec.filename}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        {formatDuration(rec.durationSeconds ? Math.round(rec.durationSeconds) : null, 'short')}
                      </TableCell>
                      <TableCell>{formatFileSize(rec.sizeBytes)}</TableCell>
                      <TableCell>{rec.sampleRate ? `${rec.sampleRate} Hz` : '-'}</TableCell>
                      <TableCell>
                        {rec.uploadedAt
                          ? new Date(rec.uploadedAt).toLocaleString()
                          : '-'}
                      </TableCell>
                      <TableCell align="right">
                        <IconButton
                          size="small"
                          onClick={() => handlePlayToggle(rec.filename)}
                          color={playingStepId === rec.filename ? 'primary' : 'default'}
                        >
                          {playingStepId === rec.filename ? (
                            <StopIcon fontSize="small" />
                          ) : (
                            <PlayArrowIcon fontSize="small" />
                          )}
                        </IconButton>
                        <IconButton
                          size="small"
                          onClick={() => setDeleteTarget(rec)}
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

      {/* Delete Confirmation Dialog */}
      <Dialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>Delete Recording</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ mb: 1 }}>
            Are you sure you want to delete this recording?
          </Typography>
          <Typography
            variant="body2"
            sx={{ fontFamily: 'monospace', color: 'text.secondary' }}
          >
            {deleteTarget?.filename}
          </Typography>
          {deleteTarget && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              {formatFileSize(deleteTarget.sizeBytes)}
              {deleteTarget.durationSeconds
                ? ` \u00b7 ${formatDuration(Math.round(deleteTarget.durationSeconds), 'short')}`
                : ''}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteTarget(null)}>Cancel</Button>
          <Button onClick={handleConfirmDelete} color="error" variant="contained">
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
