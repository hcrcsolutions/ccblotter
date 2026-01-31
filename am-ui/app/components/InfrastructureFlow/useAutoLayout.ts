'use client';

import { useMemo } from 'react';
import type { Node, Edge } from '@xyflow/react';
import type { InfrastructureTopology, InfrastructureNode, InfraServerType } from '../../types';

// =============================================================================
// LAYOUT CONSTANTS
// =============================================================================

const NODE_WIDTH = 180;          // Width of each server node in pixels
const NODE_HEIGHT = 130;         // Height of each server node (must match rendered node)
const H_GAP = 20;                // Horizontal gap between nodes
const V_GAP = 30;                // Vertical gap between rows
const COLUMN_GAP = 250;          // Gap between columns (Trunks | SBCs | SIPs | Media)
const DC_HEADER_HEIGHT = 40;     // Height for datacenter label
const DC_GAP = 60;               // Gap between datacenter groups

// Column X positions (pre-calculated for performance)
const COLUMN_X = [
  0,                                    // Trunks
  NODE_WIDTH + COLUMN_GAP,              // SBCs
  2 * (NODE_WIDTH + COLUMN_GAP),        // SIPs
  3 * (NODE_WIDTH + COLUMN_GAP),        // Media start
] as const;

// =============================================================================
// LAYOUT STRATEGY
// =============================================================================
//
// GROUPED VIEW (groupByDatacenter = true):
// Organizes nodes into 4 columns by type, grouped by datacenter vertically
//
// FLAT VIEW (groupByDatacenter = false):
// Simple 4-column layout without DC grouping, all nodes of same type together
//
// =============================================================================

// Type for our custom node data (index signature required by ReactFlow's Node<T>)
type InfraNodeData = InfrastructureNode & { [key: string]: unknown };

// Type for datacenter header node data (with index signature for ReactFlow compatibility)
type DcHeaderData = Record<string, unknown> & {
  id: string;
  region: string;
  healthyNodes: number;
  degradedNodes: number;
  unhealthyNodes: number;
  width: number;
};

// Union type for all node data
type NodeData = InfraNodeData | DcHeaderData;

interface LayoutResult {
  nodes: Node<NodeData>[];
  edges: Edge[];
}

interface LayoutOptions {
  groupByDatacenter?: boolean;
}

// Map type to node type for ReactFlow
function getNodeType(type: InfraServerType): string {
  switch (type) {
    case 'TRUNK': return 'trunkServer';
    case 'SBC': return 'sbcServer';
    case 'SIP': return 'sipServer';
    case 'MEDIA': return 'mediaServer';
    default:
      console.warn(`Unknown server type: ${type}, defaulting to mediaServer`);
      return 'mediaServer';
  }
}

/**
 * Calculate the number of columns for a square-ish media server grid.
 */
function calculateMediaGridColumns(serverCount: number): number {
  if (serverCount <= 0) return 1;
  return Math.ceil(Math.sqrt(serverCount));
}

/**
 * Create edges from topology, filtering to only include edges where both nodes exist.
 */
function createEdges(
  topology: InfrastructureTopology,
  nodeIds: Set<string>
): Edge[] {
  return topology.edges
    .filter(edge => nodeIds.has(edge.sourceId) && nodeIds.has(edge.targetId))
    .map(edge => ({
      id: edge.id,
      source: edge.sourceId,
      target: edge.targetId,
      type: 'default',
      style: { strokeWidth: 1.5, opacity: 0.4 },
      animated: (edge.activeFlows ?? 0) > 20,
    }));
}

/**
 * Layout nodes in flat 4-column view (no DC grouping).
 */
function layoutFlat(topology: InfrastructureTopology): LayoutResult {
  const nodes: Node<NodeData>[] = [];

  // Separate by type
  const trunks = topology.nodes.filter(n => n.type === 'TRUNK');
  const sbcs = topology.nodes.filter(n => n.type === 'SBC');
  const sips = topology.nodes.filter(n => n.type === 'SIP');
  const media = topology.nodes.filter(n => n.type === 'MEDIA');

  // Layout trunks (column 0)
  trunks.forEach((trunk, idx) => {
    nodes.push({
      id: trunk.id,
      type: getNodeType(trunk.type),
      position: {
        x: COLUMN_X[0],
        y: idx * (NODE_HEIGHT + V_GAP),
      },
      data: { ...trunk } as InfraNodeData,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
    });
  });

  // Layout SBCs (column 1)
  sbcs.forEach((sbc, idx) => {
    nodes.push({
      id: sbc.id,
      type: getNodeType(sbc.type),
      position: {
        x: COLUMN_X[1],
        y: idx * (NODE_HEIGHT + V_GAP),
      },
      data: { ...sbc } as InfraNodeData,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
    });
  });

  // Layout SIPs (column 2)
  sips.forEach((sip, idx) => {
    nodes.push({
      id: sip.id,
      type: getNodeType(sip.type),
      position: {
        x: COLUMN_X[2],
        y: idx * (NODE_HEIGHT + V_GAP),
      },
      data: { ...sip } as InfraNodeData,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
    });
  });

  // Layout Media servers (column 3+ as grid)
  const mediaCols = calculateMediaGridColumns(media.length);
  media.forEach((mediaNode, idx) => {
    const row = Math.floor(idx / mediaCols);
    const col = idx % mediaCols;
    nodes.push({
      id: mediaNode.id,
      type: getNodeType(mediaNode.type),
      position: {
        x: COLUMN_X[3] + col * (NODE_WIDTH + H_GAP),
        y: row * (NODE_HEIGHT + V_GAP),
      },
      data: { ...mediaNode } as InfraNodeData,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
    });
  });

  // Create edges
  const nodeIdSet = new Set(nodes.map(n => n.id));
  const edges = createEdges(topology, nodeIdSet);

  return { nodes, edges };
}

/**
 * Layout nodes grouped by datacenter.
 */
function layoutGrouped(topology: InfrastructureTopology): LayoutResult {
  const nodes: Node<NodeData>[] = [];

  // Group nodes by datacenter
  const dcGroups = new Map<string, InfrastructureNode[]>();
  topology.nodes.forEach(node => {
    const dc = node.datacenter || 'unknown';
    const group = dcGroups.get(dc);
    if (group) {
      group.push(node);
    } else {
      dcGroups.set(dc, [node]);
    }
  });

  // Sort datacenters for consistent ordering
  const sortedDcs = Array.from(dcGroups.keys()).sort();

  let currentY = 0;

  sortedDcs.forEach(dc => {
    const dcNodes = dcGroups.get(dc);
    if (!dcNodes) return;

    // Separate by type
    const trunks = dcNodes.filter(n => n.type === 'TRUNK');
    const sbcs = dcNodes.filter(n => n.type === 'SBC');
    const sips = dcNodes.filter(n => n.type === 'SIP');
    const media = dcNodes.filter(n => n.type === 'MEDIA');

    // Calculate media grid dimensions
    const mediaCols = calculateMediaGridColumns(media.length);
    const mediaRows = Math.ceil(media.length / mediaCols);

    // Calculate max height for this DC (considering all columns)
    // Guard against empty arrays to avoid negative heights
    const trunkHeight = trunks.length > 0 ? trunks.length * (NODE_HEIGHT + V_GAP) - V_GAP : 0;
    const sbcHeight = sbcs.length > 0 ? sbcs.length * (NODE_HEIGHT + V_GAP) - V_GAP : 0;
    const sipHeight = sips.length > 0 ? sips.length * (NODE_HEIGHT + V_GAP) - V_GAP : 0;
    const mediaHeight = mediaRows > 0 ? mediaRows * (NODE_HEIGHT + V_GAP) - V_GAP : 0;

    const dcHeight = Math.max(trunkHeight, sbcHeight, sipHeight, mediaHeight, NODE_HEIGHT);

    // Calculate DC header width (spans from trunks to end of media grid)
    const mediaGridWidth = mediaCols * (NODE_WIDTH + H_GAP) - H_GAP;
    const dcHeaderWidth = COLUMN_X[3] + mediaGridWidth;

    // Calculate health counts for this DC
    const healthyNodes = dcNodes.filter(n => n.healthStatus === 'HEALTHY').length;
    const degradedNodes = dcNodes.filter(n => n.healthStatus === 'DEGRADED').length;
    const unhealthyNodes = dcNodes.filter(n => n.healthStatus === 'UNHEALTHY').length;

    // Determine region (all nodes in a DC should have same region)
    const region = dcNodes[0]?.region;
    if (!region) {
      console.warn(`No region found for datacenter: ${dc}`);
    }

    // Add DC header node
    nodes.push({
      id: `dc-header-${dc}`,
      type: 'dcHeader',
      position: {
        x: 0,
        y: currentY,
      },
      data: {
        id: dc.toUpperCase(),
        region: region || 'unknown',
        healthyNodes,
        degradedNodes,
        unhealthyNodes,
        width: dcHeaderWidth,
      } as DcHeaderData,
      draggable: false,
      selectable: false,
    });

    // Start Y for this DC (after header)
    const dcStartY = currentY + DC_HEADER_HEIGHT;

    // Layout trunks (column 0)
    trunks.forEach((trunk, idx) => {
      nodes.push({
        id: trunk.id,
        type: getNodeType(trunk.type),
        position: {
          x: COLUMN_X[0],
          y: dcStartY + idx * (NODE_HEIGHT + V_GAP),
        },
        data: { ...trunk } as InfraNodeData,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      });
    });

    // Layout SBCs (column 1)
    sbcs.forEach((sbc, idx) => {
      nodes.push({
        id: sbc.id,
        type: getNodeType(sbc.type),
        position: {
          x: COLUMN_X[1],
          y: dcStartY + idx * (NODE_HEIGHT + V_GAP),
        },
        data: { ...sbc } as InfraNodeData,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      });
    });

    // Layout SIPs (column 2) - centered vertically in the DC
    const sipStartY = dcStartY + Math.max(0, (dcHeight - sipHeight) / 2);
    sips.forEach((sip, idx) => {
      nodes.push({
        id: sip.id,
        type: getNodeType(sip.type),
        position: {
          x: COLUMN_X[2],
          y: sipStartY + idx * (NODE_HEIGHT + V_GAP),
        },
        data: { ...sip } as InfraNodeData,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      });
    });

    // Layout Media servers (column 3+ as grid)
    media.forEach((mediaNode, idx) => {
      const row = Math.floor(idx / mediaCols);
      const col = idx % mediaCols;
      nodes.push({
        id: mediaNode.id,
        type: getNodeType(mediaNode.type),
        position: {
          x: COLUMN_X[3] + col * (NODE_WIDTH + H_GAP),
          y: dcStartY + row * (NODE_HEIGHT + V_GAP),
        },
        data: { ...mediaNode } as InfraNodeData,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      });
    });

    // Move to next DC
    currentY = dcStartY + dcHeight + DC_GAP;
  });

  // Create edges
  const nodeIdSet = new Set(nodes.map(n => n.id));
  const edges = createEdges(topology, nodeIdSet);

  return { nodes, edges };
}

/**
 * Hook for laying out infrastructure nodes.
 *
 * @param topology - The infrastructure topology to layout
 * @param options - Layout options
 * @param options.groupByDatacenter - Whether to group nodes by datacenter (default: true)
 */
export function useAutoLayout(
  topology: InfrastructureTopology,
  options: LayoutOptions = {}
): LayoutResult {
  const { groupByDatacenter = true } = options;

  return useMemo(() => {
    if (topology.nodes.length === 0) {
      return { nodes: [], edges: [] };
    }

    return groupByDatacenter
      ? layoutGrouped(topology)
      : layoutFlat(topology);
  }, [topology, groupByDatacenter]);
}
