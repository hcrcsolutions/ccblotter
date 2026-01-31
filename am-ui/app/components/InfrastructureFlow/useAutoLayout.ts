'use client';

import { useMemo } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { InfrastructureTopology, InfrastructureNode } from '../../types';

// =============================================================================
// LAYOUT CONSTANTS
// =============================================================================

const NODE_WIDTH = 180;          // Width of each server node in pixels
const NODE_HEIGHT = 120;         // Height of each server node (must match rendered node)
const H_GAP = 20;                // Horizontal gap between media nodes
const V_GAP = 40;                // Vertical gap between rows (allows edge routing)
const SIP_TO_MEDIA_GAP = 250;    // Horizontal gap between SIP column and media grid
const SIP_GROUP_GAP = 120;       // Vertical gap between different SIP server groups

// =============================================================================
// GRID LAYOUT STRATEGY
// =============================================================================
//
// Media servers are arranged in a responsive grid that adapts based on count:
//
// PHASE 1: SQUARE FORMATION (1-9 servers)
// ----------------------------------------
// For small groups, we arrange servers in a square-like formation for
// visual compactness. The grid grows as follows:
//
//   Count  | Grid   | Visual
//   -------|--------|------------------
//   1      | 1x1    | [M]
//   2      | 2x1    | [M][M]
//   3      | 2x2    | [M][M]
//          |        | [M]
//   4      | 2x2    | [M][M]
//          |        | [M][M]
//   5-6    | 3x2    | [M][M][M]
//          |        | [M][M][M]
//   7-9    | 3x3    | [M][M][M]
//          |        | [M][M][M]
//          |        | [M][M][M]
//
// PHASE 2: EXTENDED ROWS (10+ servers)
// ----------------------------------------
// Once we exceed 9 servers, we switch to a wider row-based layout
// with 10 servers per row. This provides a clear visual structure
// for larger deployments:
//
//   Count  | Grid   | Visual
//   -------|--------|----------------------------------
//   10-19  | 10xN   | [M][M][M][M][M][M][M][M][M][M]
//          |        | [M][M]...
//   20-29  | 10xN   | [M][M][M][M][M][M][M][M][M][M]
//          |        | [M][M][M][M][M][M][M][M][M][M]
//          |        | [M][M]...
//   etc.
//
// This two-phase approach ensures:
// - Small groups look compact and organized (square)
// - Large groups remain readable with consistent row widths
// - The SIP server is always centered vertically relative to its media group
//
// =============================================================================

const SQUARE_THRESHOLD = 9;      // Max servers for square formation
const EXTENDED_ROW_WIDTH = 10;   // Servers per row after square threshold

/**
 * Calculate the number of columns for the media server grid.
 * Uses square formation for small groups, extended rows for larger groups.
 */
function calculateGridColumns(serverCount: number): number {
  if (serverCount <= 0) return 1;

  if (serverCount <= SQUARE_THRESHOLD) {
    // Square formation: use ceiling of square root, capped at 3
    // 1→1, 2→2, 3-4→2, 5-9→3
    return Math.min(3, Math.ceil(Math.sqrt(serverCount)));
  }

  // Extended row formation for larger groups
  return EXTENDED_ROW_WIDTH;
}

// Type for our custom node data
type InfraNodeData = Record<string, unknown> & InfrastructureNode;

interface LayoutResult {
  nodes: Node<InfraNodeData>[];
  edges: Edge[];
}

/**
 * Hook for laying out infrastructure nodes in a tree structure.
 *
 * Layout structure:
 * - SIP servers positioned on the left column
 * - Each SIP's media servers arranged in a grid to the right
 * - Grid uses square formation (up to 3x3) for small groups
 * - Grid expands to 10-column rows for larger deployments
 * - Bezier curve edges connect each SIP to its media servers
 */
export function useAutoLayout(topology: InfrastructureTopology): LayoutResult {
  return useMemo(() => {
    if (topology.nodes.length === 0) {
      return { nodes: [], edges: [] };
    }

    // Separate SIP and Media servers
    const sipServers = topology.nodes.filter(n => n.type === 'SIP');
    const mediaServers = topology.nodes.filter(n => n.type === 'MEDIA');

    // Build a map of SIP -> connected media servers
    const sipToMedia = new Map<string, InfrastructureNode[]>();
    sipServers.forEach(sip => sipToMedia.set(sip.id, []));

    topology.edges.forEach(edge => {
      const mediaNode = mediaServers.find(m => m.id === edge.targetId);
      if (mediaNode && sipToMedia.has(edge.sourceId)) {
        sipToMedia.get(edge.sourceId)!.push(mediaNode);
      }
    });

    const nodes: Node<InfraNodeData>[] = [];
    const edges: Edge[] = [];
    let currentY = 0;

    // Layout each SIP server and its media servers
    sipServers.forEach((sip) => {
      const connectedMedia = sipToMedia.get(sip.id) || [];
      const mediaCount = connectedMedia.length;

      // Calculate grid dimensions based on server count
      const cols = calculateGridColumns(mediaCount);
      const rows = Math.ceil(mediaCount / cols);
      const groupHeight = Math.max(1, rows) * (NODE_HEIGHT + V_GAP) - V_GAP;

      // Position SIP server (centered vertically relative to its media group)
      const sipY = currentY + (groupHeight - NODE_HEIGHT) / 2;

      nodes.push({
        id: sip.id,
        type: 'sipServer',
        position: { x: 0, y: sipY },
        data: { ...sip } as InfraNodeData,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      });

      // Position media servers in adaptive grid
      connectedMedia.forEach((media, index) => {
        const row = Math.floor(index / cols);
        const col = index % cols;

        nodes.push({
          id: media.id,
          type: 'mediaServer',
          position: {
            x: NODE_WIDTH + SIP_TO_MEDIA_GAP + col * (NODE_WIDTH + H_GAP),
            y: currentY + row * (NODE_HEIGHT + V_GAP),
          },
          data: { ...media } as InfraNodeData,
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
        });

        // Edge from SIP to each media server
        // Bezier curves fan out naturally and route between nodes
        edges.push({
          id: `e-${sip.id}-${media.id}`,
          source: sip.id,
          target: media.id,
          type: 'default',  // bezier curve
          style: { strokeWidth: 1.5, opacity: 0.6 },
        });
      });

      currentY += groupHeight + SIP_GROUP_GAP;
    });

    return { nodes, edges };
  }, [topology]);
}
