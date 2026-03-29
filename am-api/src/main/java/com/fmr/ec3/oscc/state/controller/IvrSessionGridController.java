package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.grid.GridRequest;
import com.fmr.ec3.oscc.state.dto.grid.GridResponse;
import com.fmr.ec3.oscc.state.dto.grid.IvrSessionRow;
import com.fmr.ec3.oscc.state.exception.RedisUnavailableException;
import com.fmr.ec3.oscc.state.model.IvrSession;
import com.fmr.ec3.oscc.state.model.IvrState;
import com.fmr.ec3.oscc.state.model.IvrStep;
import com.fmr.ec3.oscc.state.service.GridFilterService;
import com.fmr.ec3.oscc.state.service.IvrSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ivr-sessions")
@RequiredArgsConstructor
public class IvrSessionGridController {

    private final IvrSessionService ivrSessionService;
    private final GridFilterService gridFilterService;

    private static final Map<String, Function<IvrSessionRow, Comparable<?>>> FIELD_ACCESSORS = new HashMap<>();
    static {
        FIELD_ACCESSORS.put("id", IvrSessionRow::getId);
        FIELD_ACCESSORS.put("callId", IvrSessionRow::getCallId);
        FIELD_ACCESSORS.put("originator", IvrSessionRow::getOriginator);
        FIELD_ACCESSORS.put("flowId", IvrSessionRow::getFlowId);
        FIELD_ACCESSORS.put("state", row -> row.getState() != null ? row.getState().name() : null);
        FIELD_ACCESSORS.put("currentStep", IvrSessionRow::getCurrentStep);
        FIELD_ACCESSORS.put("stepCount", IvrSessionRow::getStepCount);
        FIELD_ACCESSORS.put("startTime", IvrSessionRow::getStartTime);
        FIELD_ACCESSORS.put("authenticated", row -> row.isAuthenticated() ? 1 : 0);
        // duration: compute actual elapsed seconds (use endTime for finished sessions, now for active)
        FIELD_ACCESSORS.put("duration", row -> {
            if (row.getStartTime() == null) {
                return null;
            }
            Instant end = row.getEndTime() != null ? row.getEndTime() : Instant.now();
            return Duration.between(row.getStartTime(), end).getSeconds();
        });
    }

    @PostMapping("/query")
    public ResponseEntity<GridResponse<IvrSessionRow>> querySessions(@RequestBody GridRequest request) {
        try {
            List<IvrSession> sessions = ivrSessionService.getAllSessions();

            List<IvrSessionRow> rows = sessions.stream()
                    .map(this::buildRow)
                    .collect(Collectors.toList());

            Instant now = Instant.now();
            Map<String, BiPredicate<IvrSessionRow, GridRequest.FilterItem>> customFilters = new HashMap<>();
            customFilters.put("duration", (row, filter) ->
                    matchesDurationFilter(row.getStartTime(), now, filter));

            GridResponse<IvrSessionRow> response = gridFilterService.applyFilterSortPage(
                    rows, request, FIELD_ACCESSORS, customFilters);

            // Summary counts (unfiltered)
            long active = sessions.stream().filter(s -> s.getState() == IvrState.ACTIVE).count();
            long authenticating = sessions.stream().filter(s -> s.getState() == IvrState.AUTHENTICATING).count();
            long transferring = sessions.stream().filter(s -> s.getState() == IvrState.TRANSFERRING).count();
            long completed = sessions.stream().filter(s -> s.getState() == IvrState.COMPLETED).count();
            long abandoned = sessions.stream().filter(s -> s.getState() == IvrState.ABANDONED).count();
            long failed = sessions.stream().filter(s -> s.getState() == IvrState.FAILED).count();
            response.setMetadata(Map.of(
                    "active", active,
                    "authenticating", authenticating,
                    "transferring", transferring,
                    "completed", completed,
                    "abandoned", abandoned,
                    "failed", failed,
                    "total", (long) sessions.size(),
                    "sessionCount", (long) sessions.size()
            ));

            return ResponseEntity.ok(response);
        } catch (RedisUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    @GetMapping("/{id}/steps")
    public ResponseEntity<List<IvrStep>> getSessionSteps(@PathVariable String id) {
        try {
            IvrSession session = ivrSessionService.getSession(id);
            if (session == null) {
                return ResponseEntity.notFound().build();
            }

            List<IvrStep> steps = ivrSessionService.getSteps(id);
            return ResponseEntity.ok(steps);
        } catch (RedisUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private IvrSessionRow buildRow(IvrSession session) {
        return IvrSessionRow.builder()
                .id(session.getId())
                .callId(session.getCallId())
                .originator(session.getOriginator())
                .flowId(session.getFlowId())
                .state(session.getState())
                .currentStep(session.getCurrentStep())
                .stepCount(session.getStepCount())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .authenticated(session.isAuthenticated())
                .build();
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
