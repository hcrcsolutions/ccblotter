import type { GridApi, IDatasource, IGetRowsParams } from 'ag-grid-community';
import type { GridRequest, GridResponse, GridSortItem, GridFilterItem } from '../types';
import { getApiBaseUrl } from './settings';

interface GridDatasourceOptions<T> {
  /** REST endpoint path (e.g. '/agents/query') */
  endpoint: string;
  /** Called with response metadata on each successful fetch */
  onMetadata?: (metadata: Record<string, unknown>) => void;
}

/* ── shared mapping helpers ── */

function mapSortModel(columnState: { colId: string; sort?: string | null; sortIndex?: number | null }[]): GridSortItem[] {
  return columnState
    .filter(c => c.sort)
    .sort((a, b) => (a.sortIndex ?? 0) - (b.sortIndex ?? 0))
    .map(c => ({ colId: c.colId, sort: c.sort as 'asc' | 'desc' }));
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function mapFilterModel(filterModel: Record<string, any>): Record<string, GridFilterItem> {
  const mapped: Record<string, GridFilterItem> = {};
  if (filterModel) {
    for (const [colId, filter] of Object.entries(filterModel)) {
      const f = filter as { filterType?: string; type?: string; filter?: string; filterTo?: string };
      if (f.type && f.filter != null) {
        mapped[colId] = {
          filterType: (f.filterType || 'text') as 'text' | 'number',
          type: f.type,
          filter: String(f.filter),
          filterTo: f.filterTo != null ? String(f.filterTo) : undefined,
        };
      }
    }
  }
  return mapped;
}

/* ── datasource factory (used for initial load + scrolling) ── */

/**
 * Creates an IDatasource for AG Grid's infinite row model.
 * POSTs GridRequest to the server and calls successCallback with the result.
 */
export function createGridDatasource<T>(options: GridDatasourceOptions<T>): IDatasource {
  return {
    getRows(params: IGetRowsParams) {
      const { startRow, endRow, sortModel, filterModel } = params;

      const mappedSort: GridSortItem[] = (sortModel || []).map(s => ({
        colId: s.colId,
        sort: s.sort as 'asc' | 'desc',
      }));

      const request: GridRequest = {
        startRow,
        endRow,
        sortModel: mappedSort,
        filterModel: mapFilterModel(filterModel || {}),
      };

      const apiUrl = getApiBaseUrl();

      fetch(`${apiUrl}${options.endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      })
        .then(res => {
          if (!res.ok) {
            throw new Error(`Grid query failed: ${res.status}`);
          }
          return res.json() as Promise<GridResponse<T>>;
        })
        .then(data => {
          if (options.onMetadata && data.metadata) {
            options.onMetadata(data.metadata);
          }
          params.successCallback(data.rows, data.lastRow);
        })
        .catch(err => {
          console.error(`Grid datasource error (${options.endpoint}):`, err);
          params.failCallback();
        });
    },
  };
}

/* ── visible-only refresh (used by periodic timer) ── */

/**
 * Fetches fresh data for the currently visible rows and updates them in place
 * via node.setData(). No cache purge — old data stays visible until the
 * response arrives, so there is no loading flicker.
 *
 * @param countKey Optional metadata key holding the total row count (e.g.
 *   'queuedCount'). When provided and the count differs from the grid's
 *   current row count, refreshInfiniteCache() is called instead of setData()
 *   so that added/removed rows are picked up.
 */
export async function refreshVisibleRows<T>(
  api: GridApi,
  endpoint: string,
  cacheBlockSize: number,
  onMetadata?: (metadata: Record<string, unknown>) => void,
  countKey?: string,
): Promise<void> {
  try {
    const firstRow = api.getFirstDisplayedRowIndex();
    const lastRow = api.getLastDisplayedRowIndex();

    if (firstRow < 0 || lastRow < 0) {
      return;
    }

    // Align to block boundaries so the server returns complete sorted pages
    const startRow = Math.floor(firstRow / cacheBlockSize) * cacheBlockSize;
    const endRow = (Math.floor(lastRow / cacheBlockSize) + 1) * cacheBlockSize;

    const sortModel = mapSortModel(api.getColumnState());
    const filterModel = mapFilterModel(api.getFilterModel());

    const request: GridRequest = { startRow, endRow, sortModel, filterModel };
    const apiUrl = getApiBaseUrl();

    const res = await fetch(`${apiUrl}${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    });

    if (!res.ok) {
      return;
    }

    const data = (await res.json()) as GridResponse<T>;

    if (onMetadata && data.metadata) {
      onMetadata(data.metadata);
    }

    // If total row count changed, reload cache to pick up added/removed rows.
    // refreshInfiniteCache keeps old data visible while blocks reload.
    if (countKey && data.metadata?.[countKey] != null) {
      const serverCount = data.metadata[countKey] as number;
      const gridCount = api.getDisplayedRowCount();
      if (serverCount !== gridCount) {
        api.refreshInfiniteCache();
        return;
      }
    }

    // Same row count — update visible nodes in place (no flicker)
    for (let i = firstRow; i <= lastRow; i++) {
      const dataIdx = i - startRow;
      if (dataIdx >= 0 && dataIdx < data.rows.length) {
        const node = api.getDisplayedRowAtIndex(i);
        if (node) {
          node.setData(data.rows[dataIdx]);
        }
      }
    }
  } catch (err) {
    console.error(`Refresh error (${endpoint}):`, err);
  }
}
