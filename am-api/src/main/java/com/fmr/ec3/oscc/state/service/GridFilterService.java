package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.dto.grid.GridRequest;
import com.fmr.ec3.oscc.state.dto.grid.GridResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic service that applies AG Grid filterModel, sortModel, and pagination
 * to an in-memory list of rows.
 */
@Service
public class GridFilterService {

    /**
     * Apply filtering, sorting, and pagination to a list of rows.
     *
     * @param allRows        the full dataset
     * @param request        the AG Grid request (startRow, endRow, sortModel, filterModel)
     * @param fieldAccessors map of colId to getter function for sorting and generic filtering
     * @param customFilters  map of colId to custom filter predicate (checked before generic)
     * @param <T>            the row type
     * @return paginated response with lastRow set when endRow >= filtered size
     */
    public <T> GridResponse<T> applyFilterSortPage(
            List<T> allRows,
            GridRequest request,
            Map<String, Function<T, Comparable<?>>> fieldAccessors,
            Map<String, BiPredicate<T, GridRequest.FilterItem>> customFilters) {

        Stream<T> stream = allRows.stream();

        // Apply filters
        if (request.getFilterModel() != null) {
            for (Map.Entry<String, GridRequest.FilterItem> entry : request.getFilterModel().entrySet()) {
                String colId = entry.getKey();
                GridRequest.FilterItem filterItem = entry.getValue();

                // Check custom filter first
                BiPredicate<T, GridRequest.FilterItem> custom = customFilters != null
                        ? customFilters.get(colId) : null;
                if (custom != null) {
                    stream = stream.filter(row -> custom.test(row, filterItem));
                } else {
                    // Fall back to generic filtering via field accessor
                    Function<T, Comparable<?>> accessor = fieldAccessors.get(colId);
                    if (accessor != null) {
                        stream = stream.filter(row -> matchesGenericFilter(accessor.apply(row), filterItem));
                    }
                }
            }
        }

        List<T> filtered = stream.collect(Collectors.toList());
        int filteredSize = filtered.size();

        // Apply sorting
        if (request.getSortModel() != null && !request.getSortModel().isEmpty()) {
            Comparator<T> comparator = buildComparator(request.getSortModel(), fieldAccessors);
            filtered.sort(comparator);
        }

        // Apply pagination
        int startRow = Math.max(0, request.getStartRow());
        int endRow = Math.min(request.getEndRow(), filteredSize);
        List<T> page = startRow < filteredSize
                ? filtered.subList(startRow, endRow)
                : List.of();

        int lastRow = request.getEndRow() >= filteredSize ? filteredSize : -1;

        return GridResponse.<T>builder()
                .rows(page)
                .lastRow(lastRow)
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Comparator<T> buildComparator(
            List<GridRequest.SortItem> sortModel,
            Map<String, Function<T, Comparable<?>>> fieldAccessors) {

        Comparator<T> combined = null;

        for (GridRequest.SortItem sort : sortModel) {
            Function<T, Comparable<?>> accessor = fieldAccessors.get(sort.getColId());
            if (accessor == null) {
                continue;
            }

            Comparator<T> comp = (a, b) -> {
                Comparable valA = accessor.apply(a);
                Comparable valB = accessor.apply(b);
                if (valA == null && valB == null) {
                    return 0;
                }
                if (valA == null) {
                    return 1; // nulls last
                }
                if (valB == null) {
                    return -1;
                }
                return valA.compareTo(valB);
            };

            if ("desc".equalsIgnoreCase(sort.getSort())) {
                comp = comp.reversed();
            }

            combined = combined == null ? comp : combined.thenComparing(comp);
        }

        return combined != null ? combined : (a, b) -> 0;
    }

    private boolean matchesGenericFilter(Comparable<?> value, GridRequest.FilterItem filterItem) {
        if (filterItem.getFilter() == null) {
            return true;
        }

        if ("number".equals(filterItem.getFilterType())) {
            return matchesNumberFilter(value, filterItem);
        }

        // Default: text filter
        return matchesTextFilter(value, filterItem);
    }

    private boolean matchesTextFilter(Comparable<?> value, GridRequest.FilterItem filterItem) {
        String cellValue = value != null ? value.toString().toLowerCase() : "";
        String filterValue = filterItem.getFilter().toLowerCase();

        return switch (filterItem.getType()) {
            case "equals" -> cellValue.equals(filterValue);
            case "notEqual" -> !cellValue.equals(filterValue);
            case "contains" -> cellValue.contains(filterValue);
            case "notContains" -> !cellValue.contains(filterValue);
            case "startsWith" -> cellValue.startsWith(filterValue);
            case "endsWith" -> cellValue.endsWith(filterValue);
            default -> true;
        };
    }

    private boolean matchesNumberFilter(Comparable<?> value, GridRequest.FilterItem filterItem) {
        if (value == null) {
            return false;
        }

        double cellValue;
        if (value instanceof Number num) {
            cellValue = num.doubleValue();
        } else {
            try {
                cellValue = Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return false;
            }
        }

        double filterValue;
        try {
            filterValue = Double.parseDouble(filterItem.getFilter());
        } catch (NumberFormatException e) {
            return true;
        }

        return switch (filterItem.getType()) {
            case "equals" -> cellValue == filterValue;
            case "notEqual" -> cellValue != filterValue;
            case "lessThan" -> cellValue < filterValue;
            case "lessThanOrEqual" -> cellValue <= filterValue;
            case "greaterThan" -> cellValue > filterValue;
            case "greaterThanOrEqual" -> cellValue >= filterValue;
            case "inRange" -> {
                if (filterItem.getFilterTo() == null) {
                    yield true;
                }
                try {
                    double filterTo = Double.parseDouble(filterItem.getFilterTo());
                    yield cellValue >= filterValue && cellValue <= filterTo;
                } catch (NumberFormatException e) {
                    yield true;
                }
            }
            default -> true;
        };
    }
}
