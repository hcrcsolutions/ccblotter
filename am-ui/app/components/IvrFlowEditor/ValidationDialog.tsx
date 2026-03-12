'use client';

import * as React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import type { IvrPublishResult } from '../../types';

interface ValidationDialogProps {
  result: IvrPublishResult | null;
  onClose: () => void;
}

export function ValidationDialog({ result, onClose }: ValidationDialogProps) {
  if (!result) {
    return null;
  }

  const errors = result.validationIssues.filter((i) => i.severity === 'ERROR');
  const warnings = result.validationIssues.filter((i) => i.severity === 'WARNING');

  return (
    <Dialog open={true} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        {result.published ? (
          <CheckCircleIcon color="success" />
        ) : (
          <ErrorIcon color="error" />
        )}
        {result.published ? 'Flow Published' : 'Publish Failed'}
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 2 }}>
          {result.message}
        </Typography>

        {result.validationIssues.length > 0 && (
          <List dense>
            {errors.map((issue, i) => (
              <ListItem key={`err-${i}`}>
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <ErrorIcon color="error" fontSize="small" />
                </ListItemIcon>
                <ListItemText
                  primary={issue.message}
                  secondary={issue.nodeId ? `Node: ${issue.nodeId}` : undefined}
                  primaryTypographyProps={{ variant: 'body2' }}
                />
              </ListItem>
            ))}
            {warnings.map((issue, i) => (
              <ListItem key={`warn-${i}`}>
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <WarningIcon color="warning" fontSize="small" />
                </ListItemIcon>
                <ListItemText
                  primary={issue.message}
                  secondary={issue.nodeId ? `Node: ${issue.nodeId}` : undefined}
                  primaryTypographyProps={{ variant: 'body2' }}
                />
              </ListItem>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
