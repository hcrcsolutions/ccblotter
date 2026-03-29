package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.grid.GridRequest;
import com.fmr.ec3.oscc.state.dto.grid.GridResponse;
import com.fmr.ec3.oscc.state.exception.RedisUnavailableException;
import com.fmr.ec3.oscc.state.model.Call;
import com.fmr.ec3.oscc.state.service.CallService;
import com.fmr.ec3.oscc.state.service.GridFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallGridController {

    private final CallService callService;
    private final GridFilterService gridFilterService;

    private static final Map<String, Function<Call, Comparable<?>>> FIELD_ACCESSORS = new HashMap<>();
    static {
        FIELD_ACCESSORS.put("originator", Call::getOriginator);
        FIELD_ACCESSORS.put("agentName", Call::getAgentName);
        FIELD_ACCESSORS.put("state", call -> call.getState() != null ? call.getState().name() : null);
        // duration: sort by startTime reversed (ascending duration = descending startTime)
        FIELD_ACCESSORS.put("duration", call ->
                call.getStartTime() != null ? -call.getStartTime().getEpochSecond() : null);
    }

    @PostMapping("/query")
    public ResponseEntity<GridResponse<Call>> queryCalls(@RequestBody GridRequest request) {
        try {
            List<Call> calls = callService.getActiveCalls();
            int unfilteredSize = calls.size();

            Instant now = Instant.now();
            Map<String, BiPredicate<Call, GridRequest.FilterItem>> customFilters = new HashMap<>();
            customFilters.put("duration", (call, filter) ->
                    matchesDurationFilter(call.getStartTime(), now, filter));

            GridResponse<Call> response = gridFilterService.applyFilterSortPage(
                    calls, request, FIELD_ACCESSORS, customFilters);

            response.setMetadata(Map.of("activeCallCount", unfilteredSize));

            return ResponseEntity.ok(response);
        } catch (RedisUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private boolean matchesDurationFilter(Instant timestamp, Instant now, GridRequest.FilterItem filter) {
        if (timestamp == null || filter.getFilter() == null) {
            return false;
        }

        long durationSeconds = Duration.between(timestamp, now).getSeconds();

        try {
            double filterValue = Double.parseDouble(filter.getFilter());

            return switch (filter.getType()) {
                case "equals" -> durationSeconds == (long) filterValue;
                case "notEqual" -> durationSeconds != (long) filterValue;
                case "lessThan" -> durationSeconds < filterValue;
                case "lessThanOrEqual" -> durationSeconds <= filterValue;
                case "greaterThan" -> durationSeconds > filterValue;
                case "greaterThanOrEqual" -> durationSeconds >= filterValue;
                case "inRange" -> {
                    if (filter.getFilterTo() == null) {
                        yield true;
                    }
                    double filterTo = Double.parseDouble(filter.getFilterTo());
                    yield durationSeconds >= filterValue && durationSeconds <= filterTo;
                }
                default -> true;
            };
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
