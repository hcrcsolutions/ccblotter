package com.example.agentmonitor.dto.grid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AG Grid server-side response DTO.
 *
 * @param <T> the row type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridResponse<T> {

    private List<T> rows;

    /**
     * Total filtered count when endRow >= filtered size, else -1.
     * AG Grid uses this to know when it has reached the end of the data.
     */
    private int lastRow;

    /**
     * Optional metadata (e.g. queue stats, call counts for summary cards).
     */
    private Map<String, Object> metadata;
}
