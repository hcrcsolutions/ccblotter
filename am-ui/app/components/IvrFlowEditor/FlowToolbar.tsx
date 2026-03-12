'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import SaveIcon from '@mui/icons-material/Save';
import PublishIcon from '@mui/icons-material/Publish';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useRouter } from 'next/navigation';

interface FlowToolbarProps {
  flowName: string;
  flowStatus: string;
  saving: boolean;
  publishing: boolean;
  dirty: boolean;
  onSave: () => void;
  onPublish: () => void;
}

export function FlowToolbar({
  flowName,
  flowStatus,
  saving,
  publishing,
  dirty,
  onSave,
  onPublish,
}: FlowToolbarProps) {
  const router = useRouter();

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
      <Button
        size="small"
        startIcon={<ArrowBackIcon />}
        onClick={() => router.push('/ivr-flows')}
      >
        Back
      </Button>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, flex: 1 }}>
        {flowName}
      </Typography>
      <Chip
        label={flowStatus}
        size="small"
        color={flowStatus === 'PUBLISHED' ? 'success' : 'default'}
        variant="outlined"
      />
      {dirty && (
        <Chip label="Unsaved" size="small" color="warning" variant="outlined" />
      )}
      <Button
        variant="outlined"
        size="small"
        startIcon={saving ? <CircularProgress size={16} /> : <SaveIcon />}
        onClick={onSave}
        disabled={saving || !dirty}
      >
        Save
      </Button>
      <Button
        variant="contained"
        size="small"
        startIcon={publishing ? <CircularProgress size={16} /> : <PublishIcon />}
        onClick={onPublish}
        disabled={publishing}
      >
        Publish
      </Button>
    </Box>
  );
}
