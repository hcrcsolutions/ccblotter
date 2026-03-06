'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import { useAudioPlayback } from '../../hooks/useAudioPlayback';
import { getBackendUrl } from '../../lib/settings';

interface Voice {
  id: string;
  name: string;
  language: string;
  gender: string;
}

const DEFAULT_TEXT = `Hello, welcome to the contact center.

<speak>
  Your estimated wait time is <say-as interpret-as="time">2:30</say-as>.
  <break time="500ms"/>
  Please hold and an agent will be with you shortly.
</speak>`;

export default function TtsPage() {
  const [text, setText] = React.useState(DEFAULT_TEXT);
  const [voiceId, setVoiceId] = React.useState('Joanna');
  const [voices, setVoices] = React.useState<Voice[]>([]);
  const [synthesizing, setSynthesizing] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const { playingStepId, play, stop } = useAudioPlayback();
  const blobUrlRef = React.useRef<string | null>(null);

  React.useEffect(() => {
    fetch(`${getBackendUrl()}/api/tts/voices`)
      .then((res) => {
        if (!res.ok) {
          throw new Error('Failed to fetch voices');
        }
        return res.json();
      })
      .then((data: Voice[]) => setVoices(data))
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load voices'));
  }, []);

  // Revoke blob URL on unmount
  React.useEffect(() => {
    return () => {
      if (blobUrlRef.current) {
        URL.revokeObjectURL(blobUrlRef.current);
      }
    };
  }, []);

  const handleSynthesize = async () => {
    if (!text.trim()) {
      return;
    }

    setError(null);
    setSynthesizing(true);
    stop();

    // Revoke previous blob URL
    if (blobUrlRef.current) {
      URL.revokeObjectURL(blobUrlRef.current);
      blobUrlRef.current = null;
    }

    try {
      const res = await fetch(`${getBackendUrl()}/api/tts/synthesize`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text, voiceId, cacheable: true }),
      });

      if (!res.ok) {
        const body = await res.json().catch(() => null);
        throw new Error(body?.message || `Synthesis failed (${res.status})`);
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      blobUrlRef.current = url;
      play('tts-preview', url);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Synthesis failed');
    } finally {
      setSynthesizing(false);
    }
  };

  const isPlaying = playingStepId === 'tts-preview';

  return (
    <Box sx={{ p: 3 }}>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            Text-to-Speech Tester
          </Typography>

          <TextField
            label="Text or SSML"
            multiline
            minRows={6}
            maxRows={16}
            fullWidth
            value={text}
            onChange={(e) => setText(e.target.value)}
            sx={{ mb: 2, '& textarea': { fontFamily: 'monospace', fontSize: '0.875rem' } }}
          />

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <FormControl sx={{ minWidth: 200 }}>
              <InputLabel>Voice</InputLabel>
              <Select
                value={voiceId}
                label="Voice"
                onChange={(e) => setVoiceId(e.target.value)}
                size="small"
              >
                {voices.map((v) => (
                  <MenuItem key={v.id} value={v.id}>
                    {v.name} ({v.language}, {v.gender})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Button
              variant="contained"
              startIcon={
                synthesizing ? (
                  <CircularProgress size={18} color="inherit" />
                ) : isPlaying ? (
                  <StopIcon />
                ) : (
                  <PlayArrowIcon />
                )
              }
              onClick={isPlaying ? stop : handleSynthesize}
              disabled={synthesizing || !text.trim()}
            >
              {synthesizing ? 'Synthesizing...' : isPlaying ? 'Stop' : 'Play'}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}
