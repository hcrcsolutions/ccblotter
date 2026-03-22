'use client';

import * as React from 'react';
import { memo } from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  getSmoothStepPath,
  type EdgeProps,
} from '@xyflow/react';
import type { EdgeType } from '../../types';
import { EDGE_TYPE_STYLES } from './scenarioConstants';

function ScenarioDependencyEdgeComponent({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  selected,
  style,
  data,
}: EdgeProps) {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });

  const edgeData = data as Record<string, unknown> | undefined;
  const label = edgeData?.label as string | undefined;
  const edgeType = (edgeData?.edgeType as EdgeType) ?? 'SEQUENCE';
  const typeStyle = EDGE_TYPE_STYLES[edgeType];

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        interactionWidth={20}
        style={{
          stroke: selected ? '#1976d2' : typeStyle.color,
          strokeWidth: selected ? 2 : typeStyle.strokeWidth,
          strokeDasharray: selected ? undefined : typeStyle.strokeDasharray,
          ...style,
        }}
        markerEnd={`url(#scenario-dep-arrow-${edgeType})`}
      />
      {label && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              fontSize: '0.65rem',
              color: typeStyle.color,
              background: '#fff',
              padding: '1px 4px',
              borderRadius: 3,
              border: `1px solid ${typeStyle.color}`,
              pointerEvents: 'all',
            }}
          >
            {label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

export const ScenarioDependencyEdge = memo(ScenarioDependencyEdgeComponent);
