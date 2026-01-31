'use client';

import * as React from 'react';
import { useState, useMemo } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';
import Collapse from '@mui/material/Collapse';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import FilterListIcon from '@mui/icons-material/FilterList';
import CloseIcon from '@mui/icons-material/Close';
import PushPinIcon from '@mui/icons-material/PushPin';
import PushPinOutlinedIcon from '@mui/icons-material/PushPinOutlined';
import { RiUnpinFill } from 'react-icons/ri';
import { useThemeContext } from '../../context/ThemeContext';
import type { InfrastructureNode } from '../../types';

interface TopologyFilterProps {
  nodes: InfrastructureNode[];
  pinnedNodeId: string | null;
  onPinNode: (nodeId: string) => void;
  onUnpin: () => void;
}

export function TopologyFilter({
  nodes,
  pinnedNodeId,
  onPinNode,
  onUnpin,
}: TopologyFilterProps) {
  const { mode } = useThemeContext();
  const isDarkMode = mode === 'dark';
  const [filterOpen, setFilterOpen] = useState(false);
  const [searchFilter, setSearchFilter] = useState('');

  // Get pinned node details
  const pinnedNode = pinnedNodeId
    ? nodes.find(n => n.id === pinnedNodeId)
    : null;

  // Filter nodes by search
  const matchedNodes = useMemo(() => {
    if (!searchFilter.trim()) return [];
    const searchLower = searchFilter.toLowerCase();
    return nodes.filter(node =>
      node.hostname.toLowerCase().includes(searchLower) ||
      node.ipAddress.toLowerCase().includes(searchLower) ||
      node.id.toLowerCase().includes(searchLower)
    );
  }, [nodes, searchFilter]);

  const handleClearSearch = () => {
    setSearchFilter('');
  };

  return (
    <>
      {/* Filter Toggle Button */}
      {!filterOpen && (
        <IconButton
          onClick={() => setFilterOpen(true)}
          sx={{
            position: 'absolute',
            top: 10,
            left: 0,
            zIndex: 5,
            backgroundColor: isDarkMode
              ? 'rgba(18, 18, 18, 0.95)'
              : 'rgba(255, 255, 255, 0.95)',
            border: `1px solid ${isDarkMode ? '#444' : '#e0e0e0'}`,
            borderLeft: 'none',
            borderRadius: '0 8px 8px 0',
            '&:hover': {
              backgroundColor: isDarkMode
                ? 'rgba(30, 30, 30, 0.95)'
                : 'rgba(245, 245, 245, 0.95)',
            },
          }}
        >
          <FilterListIcon />
        </IconButton>
      )}

      {/* Unpin Button - shown below filter button when a node is pinned */}
      {!filterOpen && pinnedNode && (
        <IconButton
          onClick={onUnpin}
          title={`Unpin ${pinnedNode.hostname}`}
          sx={{
            position: 'absolute',
            top: 58,
            left: 0,
            zIndex: 5,
            backgroundColor: isDarkMode
              ? 'rgba(18, 18, 18, 0.95)'
              : 'rgba(255, 255, 255, 0.95)',
            border: `1px solid ${isDarkMode ? '#444' : '#e0e0e0'}`,
            borderLeft: 'none',
            borderRadius: '0 8px 8px 0',
            color: isDarkMode ? '#fff' : '#000',
            '&:hover': {
              backgroundColor: isDarkMode
                ? 'rgba(30, 30, 30, 0.95)'
                : 'rgba(245, 245, 245, 0.95)',
            },
          }}
        >
          <RiUnpinFill size={24} />
        </IconButton>
      )}

      {/* Filter Panel */}
      <Collapse
        in={filterOpen}
        orientation="horizontal"
        sx={{
          position: 'absolute',
          top: 10,
          left: 0,
          zIndex: 4,
        }}
      >
        <Paper
          sx={{
            p: 2,
            pr: 1,
            backgroundColor: isDarkMode
              ? 'rgba(18, 18, 18, 0.95)'
              : 'rgba(255, 255, 255, 0.95)',
            minWidth: 280,
            maxWidth: 320,
            position: 'relative',
            borderRadius: '0 8px 8px 0',
            backdropFilter: 'blur(8px)',
          }}
        >
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
            <Typography variant="subtitle2">
              Filter Servers
            </Typography>
            <IconButton
              size="small"
              onClick={() => setFilterOpen(false)}
              sx={{ ml: 1 }}
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>

          {/* Pinned Node Indicator */}
          {pinnedNode && (
            <Box sx={{ mb: 2 }}>
              <Chip
                icon={<PushPinIcon />}
                label={pinnedNode.hostname}
                onDelete={onUnpin}
                color="primary"
                size="small"
                sx={{ maxWidth: '100%' }}
              />
              <Typography variant="caption" color="text.secondary" display="block" mt={0.5}>
                {pinnedNode.type === 'SIP' ? 'SIP Server' : 'Media Server'} • Showing connected nodes only
              </Typography>
            </Box>
          )}

          <TextField
            size="small"
            placeholder="Search by hostname or IP..."
            value={searchFilter}
            onChange={(e) => setSearchFilter(e.target.value)}
            fullWidth
            autoFocus
          />

          <Typography variant="caption" color="text.secondary" display="block" mt={1}>
            Click pin icon to filter graph to show only that server and its connections
          </Typography>

          {searchFilter && (
            <Typography variant="body2" fontWeight="bold" color="primary" display="block" mt={1}>
              {matchedNodes.length} {matchedNodes.length === 1 ? 'match' : 'matches'}
            </Typography>
          )}

          {/* Show matched nodes (up to 5) */}
          {matchedNodes.length > 0 && matchedNodes.length <= 5 && (
            <Table size="small" sx={{ mt: 1 }}>
              <TableBody>
                {matchedNodes.map((node) => (
                  <TableRow key={node.id} hover>
                    <TableCell sx={{ py: 0.5, px: 1 }}>
                      <Typography variant="body2" noWrap sx={{ maxWidth: 180 }}>
                        {node.hostname}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {node.type === 'SIP' ? 'SIP Server' : 'Media Server'}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ py: 0.5, px: 1, width: 40 }}>
                      <IconButton
                        size="small"
                        onClick={() => {
                          onPinNode(node.id);
                          setFilterOpen(false);
                          setSearchFilter('');
                        }}
                        title={pinnedNodeId === node.id ? 'Pinned' : 'Pin to filter'}
                        disabled={pinnedNodeId === node.id}
                      >
                        {pinnedNodeId === node.id ? (
                          <PushPinIcon fontSize="small" color="primary" />
                        ) : (
                          <PushPinOutlinedIcon fontSize="small" />
                        )}
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          {searchFilter && (
            <Button
              size="small"
              onClick={handleClearSearch}
              sx={{ mt: 1 }}
            >
              Clear Search
            </Button>
          )}
        </Paper>
      </Collapse>
    </>
  );
}
