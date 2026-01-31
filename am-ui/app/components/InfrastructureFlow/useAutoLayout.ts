'use client';

import { useMemo } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { InfrastructureTopology, InfrastructureNode } from '../../types';

// =============================================================================
// LAYOUT CONSTANTS
// =============================================================================

const NODE_WIDTH = 180;          // Width of each server node in pixels
const NODE_HEIGHT = 120;         // Height of each server node (must match rendered node)
const H_GAP = 20;                // Horizontal gap between nodes
const V_GAP = 40;                // Vertical gap between rows (allows edge routing)
const SIP_TO_MEDIA_GAP = 300;    // Horizontal gap between SIP column and media grid
const SIP_V_GAP = 60;            // Vertical gap between SIP servers

// =============================================================================
// LAYOUT STRATEGY: SHARED MEDIA SERVER SUPPORT
// =============================================================================
//
// This layout supports a many-to-many relationship between SIP and Media servers.
// Multiple SIP servers can connect to the same Media server.
//
// VISUAL STRUCTURE:
// -----------------
//
//   [SIP-1] ───────────┬───────────────► [M1] [M2] [M3] ... [M11]
//                      │   ┌───────────► [M12][M13][M14]... [M22]
//   [SIP-2] ───────────┼───┤             [M23][M24][M25]... [M33]
//                      │   │             ...
//   [SIP-3] ───────────┘   │             [M111][M112]...[M120]
//                          │
//   [SIP-4] ────────────────┘            (square-ish grid)
//
// KEY DESIGN DECISIONS:
// ---------------------
// 1. SIP servers are positioned in a column on the LEFT
//    - Stacked vertically with consistent spacing
//    - Centered vertically relative to the media grid
//
// 2. Media servers are positioned in a SQUARE-ISH grid on the RIGHT
//    - Independent of SIP grouping (not nested under SIPs)
//    - Each media server appears ONCE, even if connected to multiple SIPs
//    - Grid columns = ceil(sqrt(count)) for a square-like shape
//    - 120 servers → 11x11 grid (121 slots, 120 filled)
//
// 3. Edges connect SIPs to their media servers
//    - Bezier curves provide clear visual routing
//    - Multiple edges can target the same media server
//    - Edge opacity allows overlapping edges to remain visible
//
// GRID SIZING (square-ish):
// -------------------------
//   Count  | Columns | Rows  | Shape
//   -------|---------|-------|-------
//   4      | 2       | 2     | 2x2
//   9      | 3       | 3     | 3x3
//   20     | 5       | 4     | 5x4
//   50     | 8       | 7     | ~square
//   100    | 10      | 10    | 10x10
//   120    | 11      | 11    | 11x11
//
// =============================================================================

/**
 * Calculate the number of columns for a square-ish media server grid.
 *
 * Goal: Keep the grid as close to square as possible for any count.
 *
 * Examples:
 *   Count | Columns | Rows | Shape
 *   ------|---------|------|-------
 *   1-3   | 1-2     | 1-2  | Small square
 *   4     | 2       | 2    | 2x2
 *   9     | 3       | 3    | 3x3
 *   16    | 4       | 4    | 4x4
 *   20    | 5       | 4    | 5x4
 *   50    | 8       | 7    | ~square
 *   100   | 10      | 10   | 10x10
 *   120   | 11      | 11   | 11x11
 */
function calculateGridColumns(serverCount: number): number {
  if (serverCount <= 0) return 1;

  // Use ceiling of square root for a square-ish grid
  return Math.ceil(Math.sqrt(serverCount));
}

// Type for our custom node data
type InfraNodeData = Record<string, unknown> & InfrastructureNode;

interface LayoutResult {
  nodes: Node<InfraNodeData>[];
  edges: Edge[];
}

/**
 * Hook for laying out infrastructure nodes with shared media server support.
 *
 * Layout structure:
 * - SIP servers: vertical column on the left, centered relative to media grid
 * - Media servers: independent grid on the right (each appears once)
 * - Edges: bezier curves from each SIP to its connected media servers
 *
 * This supports many-to-many relationships where multiple SIPs can
 * connect to the same media server.
 */
export function useAutoLayout(topology: InfrastructureTopology): LayoutResult {
  return useMemo(() => {
    if (topology.nodes.length === 0) {
      return { nodes: [], edges: [] };
    }

    // =======================================================================
    // STEP 1: Separate node types
    // =======================================================================
    const sipServers = topology.nodes.filter(n => n.type === 'SIP');
    const mediaServers = topology.nodes.filter(n => n.type === 'MEDIA');

    const nodes: Node<InfraNodeData>[] = [];
    const edges: Edge[] = [];

    // =======================================================================
    // STEP 2: Calculate media grid dimensions
    // =======================================================================
    const mediaCount = mediaServers.length;
    const mediaCols = calculateGridColumns(mediaCount);
    const mediaRows = Math.ceil(mediaCount / mediaCols);
    const mediaGridHeight = mediaRows * (NODE_HEIGHT + V_GAP) - V_GAP;
    const mediaGridWidth = mediaCols * (NODE_WIDTH + H_GAP) - H_GAP;

    // =======================================================================
    // STEP 3: Calculate SIP column dimensions
    // =======================================================================
    const sipCount = sipServers.length;
    const sipColumnHeight = sipCount * (NODE_HEIGHT + SIP_V_GAP) - SIP_V_GAP;

    // =======================================================================
    // STEP 4: Position SIP servers (left column, centered to media grid)
    // =======================================================================
    // Center SIP column vertically relative to media grid
    const sipStartY = Math.max(0, (mediaGridHeight - sipColumnHeight) / 2);

    sipServers.forEach((sip, index) => {
      nodes.push({
        id: sip.id,
        type: 'sipServer',
        position: {
          x: 0,
          y: sipStartY + index * (NODE_HEIGHT + SIP_V_GAP),
        },
        data: { ...sip } as InfraNodeData,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      });
    });

    // =======================================================================
    // STEP 5: Position media servers (right grid, independent of SIPs)
    // =======================================================================
    const mediaStartX = NODE_WIDTH + SIP_TO_MEDIA_GAP;
    const mediaStartY = Math.max(0, (sipColumnHeight - mediaGridHeight) / 2);

    // Create a map for quick media server position lookup (for edges)
    const mediaPositions = new Map<string, { x: number; y: number }>();

    mediaServers.forEach((media, index) => {
      const row = Math.floor(index / mediaCols);
      const col = index % mediaCols;
      const position = {
        x: mediaStartX + col * (NODE_WIDTH + H_GAP),
        y: mediaStartY + row * (NODE_HEIGHT + V_GAP),
      };

      mediaPositions.set(media.id, position);

      nodes.push({
        id: media.id,
        type: 'mediaServer',
        position,
        data: { ...media } as InfraNodeData,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      });
    });

    // =======================================================================
    // STEP 6: Create edges from topology (supports shared media servers)
    // =======================================================================
    // Each edge in topology.edges creates a visual edge
    // Multiple SIPs can have edges to the same media server
    topology.edges.forEach((edge) => {
      // Only create edge if both source (SIP) and target (media) exist
      const sipExists = sipServers.some(s => s.id === edge.sourceId);
      const mediaExists = mediaPositions.has(edge.targetId);

      if (sipExists && mediaExists) {
        edges.push({
          id: edge.id,
          source: edge.sourceId,
          target: edge.targetId,
          type: 'default',  // bezier curve
          style: { strokeWidth: 1.5, opacity: 0.5 },
        });
      }
    });

    return { nodes, edges };
  }, [topology]);
}
