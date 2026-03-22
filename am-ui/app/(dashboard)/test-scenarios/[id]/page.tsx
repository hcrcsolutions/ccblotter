'use client';

import * as React from 'react';
import { useParams } from 'next/navigation';
import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Alert from '@mui/material/Alert';
import Typography from '@mui/material/Typography';
import { getBackendUrl } from '../../../lib/settings';
import { ScenarioEditor } from '../../../components/ScenarioEditor/ScenarioEditor';

export default function ScenarioEditorPage() {
  const params = useParams();
  const scenarioId = params.id as string;
  const [scenario, setScenario] = React.useState<{
    id: string;
    name: string;
    status: string;
    description: string | null;
  } | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    async function loadScenario() {
      try {
        const res = await fetch(`${getBackendUrl()}/api/v1/test-scenarios/${scenarioId}`);
        if (!res.ok) {
          throw new Error('Failed to load scenario');
        }
        const data = await res.json();
        setScenario(data);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load scenario');
      } finally {
        setLoading(false);
      }
    }
    loadScenario();
  }, [scenarioId]);

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <LinearProgress />
      </Box>
    );
  }

  if (error || !scenario) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{error || 'Scenario not found'}</Alert>
      </Box>
    );
  }

  return <ScenarioEditor scenario={scenario} />;
}
