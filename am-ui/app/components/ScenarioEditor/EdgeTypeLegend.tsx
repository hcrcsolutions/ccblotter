'use client';

import { Panel } from '@xyflow/react';
import type { EdgeType } from '../../types';
import { EDGE_TYPE_LABELS, EDGE_TYPE_STYLES } from './scenarioConstants';

const EDGE_TYPES: EdgeType[] = ['SEQUENCE', 'SYNC', 'TRIGGER', 'WAIT_FOR'];

export function EdgeTypeLegend() {
  return (
    <Panel position="top-right">
      <div
        style={{
          background: '#fff',
          border: '1px solid #e0e0e0',
          borderRadius: 4,
          padding: '6px 10px',
          fontSize: '0.7rem',
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
        }}
      >
        {EDGE_TYPES.map((et) => {
          const s = EDGE_TYPE_STYLES[et];
          return (
            <div key={et} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <svg width={28} height={10}>
                <line
                  x1={0}
                  y1={5}
                  x2={28}
                  y2={5}
                  stroke={s.color}
                  strokeWidth={2}
                  strokeDasharray={s.strokeDasharray ?? 'none'}
                />
              </svg>
              <span style={{ color: '#424242' }}>{EDGE_TYPE_LABELS[et]}</span>
            </div>
          );
        })}
      </div>
    </Panel>
  );
}
