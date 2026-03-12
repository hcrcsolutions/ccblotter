'use client';

import * as React from 'react';
import { useParams } from 'next/navigation';
import Box from '@mui/material/Box';
import LinearProgress from '@mui/material/LinearProgress';
import Alert from '@mui/material/Alert';
import Typography from '@mui/material/Typography';
import { getBackendUrl } from '../../../lib/settings';
import { IvrFlowEditor } from '../../../components/IvrFlowEditor/IvrFlowEditor';

export default function FlowEditorPage() {
  const params = useParams();
  const flowId = params.id as string;
  const [flow, setFlow] = React.useState<{ id: string; name: string; status: string } | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    async function loadFlow() {
      try {
        const res = await fetch(`${getBackendUrl()}/api/v1/ivr/flows/${flowId}`);
        if (!res.ok) {
          throw new Error('Failed to load flow');
        }
        const data = await res.json();
        setFlow(data);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load flow');
      } finally {
        setLoading(false);
      }
    }
    loadFlow();
  }, [flowId]);

  if (loading) {
    return (
      <Box sx={{ p: 3 }}>
        <LinearProgress />
      </Box>
    );
  }

  if (error || !flow) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{error || 'Flow not found'}</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      <IvrFlowEditor flowId={flowId} flowName={flow.name} flowStatus={flow.status} />
    </Box>
  );
}
