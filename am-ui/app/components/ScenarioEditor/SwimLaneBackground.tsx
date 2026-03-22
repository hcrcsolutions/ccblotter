'use client';

import * as React from 'react';
import { useViewport, useReactFlow } from '@xyflow/react';
import type { ScenarioActor } from '../../types';
import { ROLE_COLORS, ROLE_LABELS } from './scenarioConstants';
import {
  LANE_WIDTH,
  LANE_PITCH,
  LANE_HEADER_HEIGHT,
} from './useScenarioEditor';

interface SwimLaneBackgroundProps {
  actors: ScenarioActor[];
  selectedActorId: string | null;
  onSelectActor: (id: string) => void;
  onDeleteActor: (id: string) => void;
}

/**
 * Renders actor swim-lane columns.
 * Uses the viewport transform so everything pans/zooms with the canvas.
 */
export function SwimLaneBackground({
  actors,
  selectedActorId,
  onSelectActor,
  onDeleteActor,
}: SwimLaneBackgroundProps) {
  const { x, y, zoom } = useViewport();
  const { getNodes } = useReactFlow();

  // Compute lane height: cover from 0 to the bottom-most node + padding
  const maxY = React.useMemo(() => {
    const allNodes = getNodes();
    if (allNodes.length === 0) {
      return 600;
    }
    return Math.max(600, ...allNodes.map((n) => (n.position?.y ?? 0) + 160));
  }, [getNodes, actors]);

  return (
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        transformOrigin: '0 0',
        transform: `translate(${x}px, ${y}px) scale(${zoom})`,
        pointerEvents: 'none',
        zIndex: 0,
      }}
    >
      {/* ── Actor lanes ── */}
      {actors.map((actor, idx) => {
        const roleColor = ROLE_COLORS[actor.role];
        const isSelected = selectedActorId === actor.id;

        return (
          <div
            key={actor.id}
            style={{
              position: 'absolute',
              left: idx * LANE_PITCH,
              top: 0,
              width: LANE_WIDTH,
              height: maxY,
              borderRight: '1px solid #e0e0e0',
              borderLeft: idx === 0 ? '1px solid #e0e0e0' : undefined,
              background: isSelected ? `${roleColor}08` : `${roleColor}04`,
            }}
          >
            {/* Lane header */}
            <div
              style={{
                height: LANE_HEADER_HEIGHT,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                borderBottom: `2px solid ${roleColor}`,
                background: `${roleColor}12`,
                pointerEvents: 'all',
                cursor: 'pointer',
                userSelect: 'none',
                position: 'relative',
              }}
              onClick={() => onSelectActor(actor.id)}
            >
              <span
                style={{
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  color: '#212121',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  maxWidth: LANE_WIDTH - 40,
                }}
              >
                {actor.name}
              </span>
              <span
                style={{
                  fontSize: '0.65rem',
                  fontWeight: 600,
                  color: roleColor,
                  marginTop: 2,
                }}
              >
                {ROLE_LABELS[actor.role]}
              </span>
              {/* Delete button */}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onDeleteActor(actor.id);
                }}
                style={{
                  position: 'absolute',
                  top: 4,
                  right: 4,
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: '0.7rem',
                  color: '#9e9e9e',
                  padding: '2px 4px',
                  borderRadius: 4,
                  lineHeight: 1,
                  pointerEvents: 'all',
                }}
                title="Remove actor"
              >
                ✕
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
