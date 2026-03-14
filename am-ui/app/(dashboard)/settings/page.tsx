'use client';

import * as React from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemSecondaryAction from '@mui/material/ListItemSecondaryAction';
import IconButton from '@mui/material/IconButton';
import DeleteIcon from '@mui/icons-material/Delete';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import { useWebSocketContext } from '../../context/WebSocketContext';
import { isServer, getLocalStorageItem, setLocalStorageItem } from '../../lib/ssr';

const STORAGE_KEY = 'oscc-admin-settings';

interface BackendConfig {
  name: string;
  url: string;
  ivrServerUrl: string;
}

interface Settings {
  activeConfigName: string;
  configs: BackendConfig[];
}

const DEFAULT_IVR_SERVER_URL = 'http://localhost:8082';

const DEFAULT_CONFIGS: BackendConfig[] = [
  { name: 'Local (HTTPS)', url: 'https://localhost:8443', ivrServerUrl: DEFAULT_IVR_SERVER_URL },
  { name: 'Local (HTTP)', url: 'http://localhost:8080', ivrServerUrl: DEFAULT_IVR_SERVER_URL },
];

const DEFAULT_SETTINGS: Settings = {
  activeConfigName: 'Local (HTTPS)',
  configs: DEFAULT_CONFIGS,
};

function getStoredSettings(): Settings {
  if (isServer()) return DEFAULT_SETTINGS;
  try {
    const stored = getLocalStorageItem(STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored);
      // Migration: handle old format with just backendUrl
      if (parsed.backendUrl && !parsed.configs) {
        return {
          activeConfigName: 'Custom',
          configs: [
            ...DEFAULT_CONFIGS,
            { name: 'Custom', url: parsed.backendUrl, ivrServerUrl: parsed.ivrServerUrl || DEFAULT_IVR_SERVER_URL },
          ],
        };
      }
      const merged = { ...DEFAULT_SETTINGS, ...parsed };
      // Migration: add ivrServerUrl to configs that don't have it
      merged.configs = merged.configs.map((c: BackendConfig) => ({
        ...c,
        ivrServerUrl: c.ivrServerUrl || parsed.ivrServerUrl || DEFAULT_IVR_SERVER_URL,
      }));
      // Validate that activeConfigName points to a valid config
      if (!merged.configs.some((c: BackendConfig) => c.name === merged.activeConfigName)) {
        merged.activeConfigName = merged.configs[0]?.name ?? DEFAULT_SETTINGS.activeConfigName;
      }
      return { activeConfigName: merged.activeConfigName, configs: merged.configs };
    }
  } catch (e) {
    console.error('Failed to load settings:', e);
  }
  return DEFAULT_SETTINGS;
}

function saveSettings(settings: Settings): void {
  const success = setLocalStorageItem(STORAGE_KEY, JSON.stringify(settings));
  if (!success) {
    console.error('Failed to save settings: localStorage unavailable');
  }
}

export default function SettingsPage() {
  const [settings, setSettings] = React.useState<Settings>(DEFAULT_SETTINGS);
  const [newName, setNewName] = React.useState('');
  const [newUrl, setNewUrl] = React.useState('');
  const [newIvrUrl, setNewIvrUrl] = React.useState(DEFAULT_IVR_SERVER_URL);
  const [saved, setSaved] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const { reconnect } = useWebSocketContext();

  // Load settings on mount
  React.useEffect(() => {
    setSettings(getStoredSettings());
  }, []);

  // Find active config with fallback to first config if not found
  const activeConfig = settings.configs.find(c => c.name === settings.activeConfigName)
    ?? settings.configs[0]
    ?? DEFAULT_CONFIGS[0];

  const handleSelectConfig = (configName: string) => {
    const newSettings = { ...settings, activeConfigName: configName };
    setSettings(newSettings);
    saveSettings(newSettings);
    setSaved(true);
    // Reconnect WebSocket to new backend
    reconnect();
  };

  const handleDeleteConfig = (configName: string) => {
    // Don't allow deleting the active config
    if (configName === settings.activeConfigName) {
      setError('Cannot delete the active configuration');
      return;
    }

    const newConfigs = settings.configs.filter(c => c.name !== configName);
    const newSettings = { ...settings, configs: newConfigs };
    setSettings(newSettings);
    saveSettings(newSettings);
    setSaved(true);
    setError(null);
  };

  const handleAddConfig = () => {
    if (!newName.trim()) {
      setError('Please enter a configuration name');
      return;
    }

    if (settings.configs.some(c => c.name === newName.trim())) {
      setError('A configuration with this name already exists');
      return;
    }

    // Validate Backend URL
    try {
      new URL(newUrl);
    } catch {
      setError('Please enter a valid Backend URL');
      return;
    }

    // Validate IVR Server URL
    try {
      new URL(newIvrUrl);
    } catch {
      setError('Please enter a valid IVR Server URL');
      return;
    }

    const newConfig: BackendConfig = {
      name: newName.trim(),
      url: newUrl.replace(/\/+$/, ''),
      ivrServerUrl: newIvrUrl.replace(/\/+$/, ''),
    };

    const newSettings = {
      ...settings,
      configs: [...settings.configs, newConfig],
    };
    setSettings(newSettings);
    saveSettings(newSettings);
    setNewName('');
    setNewUrl('');
    setNewIvrUrl(DEFAULT_IVR_SERVER_URL);
    setSaved(true);
    setError(null);
  };

  const handleResetToDefaults = () => {
    setSettings(DEFAULT_SETTINGS);
    saveSettings(DEFAULT_SETTINGS);
    setSaved(true);
  };

  return (
    <Box sx={{ p: 3, maxWidth: 700 }}>
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            Active Configuration
          </Typography>

          <Alert severity="info" sx={{ mb: 2 }}>
            <strong>{activeConfig.name}</strong>
            <Box sx={{ display: 'flex', gap: 4, mt: 0.5 }}>
              <Typography variant="body2">Backend: {activeConfig.url}</Typography>
              <Typography variant="body2">IVR Server: {activeConfig.ivrServerUrl}</Typography>
            </Box>
          </Alert>

          <Typography variant="body2" color="text.secondary">
            Changes will take effect after refreshing the page.
          </Typography>
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            Saved Configurations
          </Typography>

          <List dense>
            {settings.configs.map((config) => (
              <ListItem
                key={config.name}
                disablePadding
                sx={{
                  bgcolor: config.name === settings.activeConfigName
                    ? 'action.selected'
                    : 'transparent',
                  borderRadius: 1,
                  mb: 0.5,
                }}
              >
                <ListItemButton onClick={() => handleSelectConfig(config.name)} sx={{ pr: 6 }}>
                  {config.name === settings.activeConfigName && (
                    <CheckIcon sx={{ mr: 1, color: 'success.main', flexShrink: 0 }} fontSize="small" />
                  )}
                  <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0, flex: 1 }}>
                    <Typography variant="body2" sx={{ fontWeight: 500 }}>
                      {config.name}
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 3 }}>
                      <Typography variant="caption" color="text.secondary" noWrap>
                        Backend: {config.url}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" noWrap>
                        IVR: {config.ivrServerUrl}
                      </Typography>
                    </Box>
                  </Box>
                </ListItemButton>
                <ListItemSecondaryAction>
                  <IconButton
                    edge="end"
                    size="small"
                    onClick={() => handleDeleteConfig(config.name)}
                    disabled={config.name === settings.activeConfigName}
                  >
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </ListItemSecondaryAction>
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            Add New Configuration
          </Typography>

          <TextField
            fullWidth
            label="Configuration Name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="e.g., Production, Staging"
            sx={{ mb: 2 }}
          />

          <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
            <TextField
              label="Backend URL"
              value={newUrl}
              onChange={(e) => setNewUrl(e.target.value)}
              placeholder="https://example.com:8443"
              sx={{ flex: 1 }}
            />
            <TextField
              label="IVR Server URL"
              value={newIvrUrl}
              onChange={(e) => setNewIvrUrl(e.target.value)}
              placeholder={DEFAULT_IVR_SERVER_URL}
              sx={{ flex: 1 }}
            />
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button variant="contained" onClick={handleAddConfig}>
              Add Configuration
            </Button>
            <Button variant="outlined" onClick={handleResetToDefaults}>
              Reset to Defaults
            </Button>
          </Box>
        </CardContent>
      </Card>

      <Snackbar
        open={saved}
        autoHideDuration={5000}
        onClose={() => setSaved(false)}
        message="Settings saved"
        action={
          <IconButton
            size="small"
            aria-label="close"
            color="inherit"
            onClick={() => setSaved(false)}
          >
            <CloseIcon fontSize="small" />
          </IconButton>
        }
      />
    </Box>
  );
}
