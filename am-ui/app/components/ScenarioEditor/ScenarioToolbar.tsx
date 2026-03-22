'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SaveIcon from '@mui/icons-material/Save';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import HistoryIcon from '@mui/icons-material/History';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import AddIcon from '@mui/icons-material/Add';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import type { ValidationResult } from './validateScenario';

interface ScenarioToolbarProps {
  name: string;
  version: number;
  dirty: boolean;
  saving: boolean;
  validation: ValidationResult;
  onSave: () => void;
  onRun: () => void;
  onHistory: () => void;
  onAddActor: () => void;
  onAddStep: () => void;
  onTidyLayout: () => void;
}

export function ScenarioToolbar({
  name, version, dirty, saving, validation, onSave, onRun, onHistory, onAddActor, onAddStep, onTidyLayout,
}: ScenarioToolbarProps) {
  const router = useRouter();

  const statusLabel = validation.valid ? 'READY' : 'DRAFT';

  const issuesSummary = validation.issues.length > 0
    ? validation.issues.map((i) => `${i.severity === 'error' ? 'Error' : 'Warning'}: ${i.message}`).join('\n')
    : 'All checks passed';

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        px: 2,
        py: 1,
        borderBottom: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper',
      }}
    >
      <IconButton onClick={() => router.push('/test-scenarios')} size="small">
        <ArrowBackIcon />
      </IconButton>

      <Typography variant="h6" sx={{ fontWeight: 500 }}>
        {name}
      </Typography>

      <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{issuesSummary}</span>}>
        <Chip
          label={statusLabel}
          size="small"
          color={validation.valid ? 'success' : 'default'}
          variant="outlined"
        />
      </Tooltip>

      {version > 0 && (
        <Typography variant="caption" color="text.secondary">
          v{version}
        </Typography>
      )}

      <Divider orientation="vertical" flexItem />

      <Button
        variant="outlined"
        size="small"
        startIcon={<PersonAddIcon />}
        onClick={onAddActor}
      >
        Add Actor
      </Button>

      <Button
        variant="outlined"
        size="small"
        startIcon={<AddIcon />}
        onClick={onAddStep}
      >
        Add Step
      </Button>

      <Tooltip title="Tidy Layout">
        <IconButton size="small" onClick={onTidyLayout}>
          <AutoFixHighIcon fontSize="small" />
        </IconButton>
      </Tooltip>

      <Box sx={{ flex: 1 }} />

      {dirty && (
        <Typography variant="caption" color="warning.main">
          Unsaved changes
        </Typography>
      )}

      <Button
        variant="outlined"
        size="small"
        startIcon={<HistoryIcon />}
        onClick={onHistory}
      >
        History
      </Button>

      <Button
        variant="outlined"
        size="small"
        color="success"
        startIcon={<PlayArrowIcon />}
        onClick={onRun}
        disabled={dirty || !validation.valid}
      >
        Run
      </Button>

      <Button
        variant="contained"
        size="small"
        startIcon={saving ? <CircularProgress size={16} /> : <SaveIcon />}
        onClick={onSave}
        disabled={saving || !dirty}
      >
        {saving ? 'Saving...' : 'Save'}
      </Button>
    </Box>
  );
}
