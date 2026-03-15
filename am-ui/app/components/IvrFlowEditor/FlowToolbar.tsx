'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import IconButton from '@mui/material/IconButton';
import Drawer from '@mui/material/Drawer';
import TextField from '@mui/material/TextField';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import SaveIcon from '@mui/icons-material/Save';
import PublishIcon from '@mui/icons-material/Publish';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import SettingsIcon from '@mui/icons-material/Settings';
import { useRouter } from 'next/navigation';
import { getBackendUrl } from '../../lib/settings';
import { useBusinessUnits } from '../../hooks/useBusinessUnits';

interface FlowToolbarProps {
  flowId: string;
  flowName: string;
  flowStatus: string;
  flowDescription: string | null;
  flowBusinessUnit: string | null;
  saving: boolean;
  publishing: boolean;
  dirty: boolean;
  onSave: () => void;
  onPublish: () => void;
  onFlowUpdate: (updates: { name?: string; description?: string | null; businessUnit?: string | null }) => void;
}

export function FlowToolbar({
  flowId,
  flowName,
  flowStatus,
  flowDescription,
  flowBusinessUnit,
  saving,
  publishing,
  dirty,
  onSave,
  onPublish,
  onFlowUpdate,
}: FlowToolbarProps) {
  const router = useRouter();
  const { businessUnits } = useBusinessUnits();
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  const [settingsName, setSettingsName] = React.useState(flowName);
  const [settingsDescription, setSettingsDescription] = React.useState(flowDescription ?? '');
  const [settingsBusinessUnit, setSettingsBusinessUnit] = React.useState(flowBusinessUnit ?? '');
  const [settingsSaving, setSettingsSaving] = React.useState(false);
  const [settingsError, setSettingsError] = React.useState<string | null>(null);

  const handleOpenDrawer = () => {
    setSettingsName(flowName);
    setSettingsDescription(flowDescription ?? '');
    setSettingsBusinessUnit(flowBusinessUnit ?? '');
    setSettingsError(null);
    setDrawerOpen(true);
  };

  const handleSaveSettings = async () => {
    setSettingsSaving(true);
    setSettingsError(null);
    try {
      const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows/${flowId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: settingsName,
          description: settingsDescription || null,
          businessUnit: settingsBusinessUnit || null,
        }),
      });
      if (!res.ok) {
        throw new Error('Failed to update flow settings');
      }
      onFlowUpdate({
        name: settingsName,
        description: settingsDescription || null,
        businessUnit: settingsBusinessUnit || null,
      });
      setDrawerOpen(false);
    } catch (e) {
      setSettingsError(e instanceof Error ? e.message : 'Failed to save');
    } finally {
      setSettingsSaving(false);
    }
  };

  return (
    <>
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
        <IconButton size="small" onClick={handleOpenDrawer} title="Flow settings">
          <SettingsIcon fontSize="small" />
        </IconButton>
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

      <Drawer anchor="right" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box sx={{ width: 360, p: 3, display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Typography variant="h6">Flow Settings</Typography>
          <TextField
            label="Name"
            value={settingsName}
            onChange={(e) => setSettingsName(e.target.value)}
            fullWidth
            size="small"
          />
          <TextField
            label="Description"
            value={settingsDescription}
            onChange={(e) => setSettingsDescription(e.target.value)}
            fullWidth
            size="small"
            multiline
            InputLabelProps={{ shrink: true }}
            sx={{
              '& .MuiInputBase-root': {
                minHeight: '100px',
                alignItems: 'flex-start',
              },
            }}
          />
          <FormControl fullWidth size="small">
            <InputLabel>Business Unit</InputLabel>
            <Select
              value={settingsBusinessUnit}
              label="Business Unit"
              onChange={(e) => setSettingsBusinessUnit(e.target.value)}
            >
              <MenuItem value="">
                <em>None</em>
              </MenuItem>
              {businessUnits.map((bu) => (
                <MenuItem key={bu} value={bu}>
                  {bu}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {settingsError && (
            <Typography color="error" variant="body2">
              {settingsError}
            </Typography>
          )}
          <Button
            variant="contained"
            onClick={handleSaveSettings}
            disabled={settingsSaving || !settingsName.trim()}
            startIcon={settingsSaving ? <CircularProgress size={16} /> : <SaveIcon />}
          >
            Save Settings
          </Button>
        </Box>
      </Drawer>
    </>
  );
}
