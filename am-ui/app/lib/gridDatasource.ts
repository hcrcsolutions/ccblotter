import type { IDatasource, IGetRowsParams } from 'ag-grid-community';
import type { GridRequest, GridResponse, GridSortItem, GridFilterItem } from '../types';
import { getApiBaseUrl } from './settings';

interface GridDatasourceOptions<T> {
  /** REST endpoint path (e.g. '/agents/query') */
  endpoint: string;
  /** Called with response metadata on each successful fetch */
  onMetadata?: (metadata: Record<string, unknown>) => void;
}

/**
 * Creates an IDatasource for AG Grid's infinite row model.
 * POSTs GridRequest to the server and calls successCallback with the result.
 */
export function createGridDatasource<T>(options: GridDatasourceOptions<T>): IDatasource {
  return {
    getRows(params: IGetRowsParams) {
      const { startRow, endRow, sortModel, filterModel } = params;

      // Map AG Grid sort model to our GridSortItem format
      const mappedSort: GridSortItem[] = (sortModel || []).map(s => ({
        colId: s.colId,
        sort: s.sort as 'asc' | 'desc',
      }));

      // Map AG Grid filter model to our GridFilterItem format
      const mappedFilter: Record<string, GridFilterItem> = {};
      if (filterModel) {
        for (const [colId, filter] of Object.entries(filterModel)) {
          const f = filter as { filterType?: string; type?: string; filter?: string; filterTo?: string };
          if (f.type && f.filter != null) {
            mappedFilter[colId] = {
              filterType: (f.filterType || 'text') as 'text' | 'number',
              type: f.type,
              filter: String(f.filter),
              filterTo: f.filterTo != null ? String(f.filterTo) : undefined,
            };
          }
        }
      }

      const request: GridRequest = {
        startRow,
        endRow,
        sortModel: mappedSort,
        filterModel: mappedFilter,
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
