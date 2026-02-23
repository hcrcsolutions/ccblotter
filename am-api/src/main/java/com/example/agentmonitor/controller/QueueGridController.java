package com.example.agentmonitor.controller;

import com.example.agentmonitor.dto.grid.GridRequest;
import com.example.agentmonitor.dto.grid.GridResponse;
import com.example.agentmonitor.exception.RedisUnavailableException;
import com.example.agentmonitor.model.QueuedCall;
import com.example.agentmonitor.service.GridFilterService;
import com.example.agentmonitor.service.QueueService;
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
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueGridController {

    private final QueueService queueService;
    private final GridFilterService gridFilterService;

    private static final Map<String, Function<QueuedCall, Comparable<?>>> FIELD_ACCESSORS = new HashMap<>();
    static {
        FIELD_ACCESSORS.put("originator", QueuedCall::getOriginator);
        FIELD_ACCESSORS.put("skill", QueuedCall::getSkill);
        FIELD_ACCESSORS.put("priority", QueuedCall::getPriority);
        // waitTime: sort by queuedAt reversed (ascending wait = descending queuedAt)
        FIELD_ACCESSORS.put("waitTime", call ->
                call.getQueuedAt() != null ? -call.getQueuedAt().getEpochSecond() : null);
    }

    @PostMapping("/query")
    public ResponseEntity<GridResponse<QueuedCall>> queryQueue(@RequestBody GridRequest request) {
        try {
            List<QueuedCall> calls = queueService.getQueuedCalls();

            Instant now = Instant.now();
            Map<String, BiPredicate<QueuedCall, GridRequest.FilterItem>> customFilters = new HashMap<>();
            customFilters.put("waitTime", (call, filter) ->
                    matchesDurationFilter(call.getQueuedAt(), now, filter));

            GridResponse<QueuedCall> response = gridFilterService.applyFilterSortPage(
                    calls, request, FIELD_ACCESSORS, customFilters);

            response.setMetadata(queueService.getQueueStats());

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
