package com.fmr.ec3.oscc.state.dto.grid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AG Grid server-side request DTO.
 * Sent as POST body to grid query endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridRequest {

    private int startRow;

    private int endRow;

    private List<SortItem> sortModel;

    private Map<String, FilterItem> filterModel;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortItem {

        private String colId;

        private String sort;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterItem {

        private String filterType;

        private String type;

        private String filter;

        private String filterTo;
    }
}
