package com.fmr.ec3.oscc.state.controller;

import com.fmr.ec3.oscc.state.dto.grid.AgentRow;
import com.fmr.ec3.oscc.state.dto.grid.GridRequest;
import com.fmr.ec3.oscc.state.dto.grid.GridResponse;
import com.fmr.ec3.oscc.state.exception.RedisUnavailableException;
import com.fmr.ec3.oscc.state.model.Agent;
import com.fmr.ec3.oscc.state.model.AgentState;
import com.fmr.ec3.oscc.state.model.Call;
import com.fmr.ec3.oscc.state.service.AgentService;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentGridController {

    private final AgentService agentService;
    private final CallService callService;
    private final GridFilterService gridFilterService;

    private static final Map<String, Function<AgentRow, Comparable<?>>> FIELD_ACCESSORS = new HashMap<>();
    static {
        FIELD_ACCESSORS.put("id", AgentRow::getId);
        FIELD_ACCESSORS.put("name", AgentRow::getName);
        FIELD_ACCESSORS.put("state", row -> row.getState() != null ? row.getState().name() : null);
        FIELD_ACCESSORS.put("currentCaller", AgentRow::getCurrentCaller);
        FIELD_ACCESSORS.put("lastCaller", AgentRow::getLastCallOriginator);
        FIELD_ACCESSORS.put("lastCallTime", AgentRow::getLastCallEndTime);
        FIELD_ACCESSORS.put("lastCallDuration", AgentRow::getLastCallDurationSeconds);
        // Computed duration columns: sort by underlying timestamp (reversed)
        FIELD_ACCESSORS.put("timeInStatus", row ->
                row.getStateChangedAt() != null ? reverseInstant(row.getStateChangedAt()) : null);
        FIELD_ACCESSORS.put("callDuration", row ->
                row.getCallStartTime() != null ? reverseInstant(row.getCallStartTime()) : null);
        FIELD_ACCESSORS.put("idleTime", row -> {
            if (row.getState() == AgentState.ONLINE && row.getLastCallEndTime() != null) {
                return reverseInstant(row.getLastCallEndTime());
            }
            return null;
        });
    }

    /**
     * Reverse an Instant for sorting: ascending duration = descending timestamp.
     * Returns the negated epoch second so natural sort order gives longest-first.
     */
    private static Long reverseInstant(Instant instant) {
        return -instant.getEpochSecond();
    }

    @PostMapping("/query")
    public ResponseEntity<GridResponse<AgentRow>> queryAgents(@RequestBody GridRequest request) {
        try {
            List<Agent> agents = agentService.getAllAgents();
            List<Call> activeCalls = callService.getActiveCalls();

            // Build call lookup by agentId
            Map<String, Call> callsByAgent = activeCalls.stream()
                    .collect(Collectors.toMap(Call::getAgentId, c -> c, (a, b) -> a));

            // Enrich agents with call data
            List<AgentRow> rows = agents.stream()
                    .map(agent -> buildAgentRow(agent, callsByAgent.get(agent.getId())))
                    .collect(Collectors.toList());

            // Build custom filters for computed duration columns
            Instant now = Instant.now();
            Map<String, BiPredicate<AgentRow, GridRequest.FilterItem>> customFilters = new HashMap<>();
            customFilters.put("timeInStatus", (row, filter) ->
                    matchesDurationFilter(row.getStateChangedAt(), now, filter));
            customFilters.put("callDuration", (row, filter) ->
                    matchesDurationFilter(row.getCallStartTime(), now, filter));
            customFilters.put("idleTime", (row, filter) -> {
                if (row.getState() != AgentState.ONLINE || row.getLastCallEndTime() == null) {
                    return false;
                }
                return matchesDurationFilter(row.getLastCallEndTime(), now, filter);
            });

            GridResponse<AgentRow> response = gridFilterService.applyFilterSortPage(
                    rows, request, FIELD_ACCESSORS, customFilters);

            // Summary counts for status cards (unfiltered)
            long online = agents.stream().filter(a -> a.getState() == AgentState.ONLINE).count();
            long onCall = agents.stream().filter(a -> a.getState() == AgentState.ON_CALL).count();
            long away = agents.stream().filter(a -> a.getState() == AgentState.AWAY).count();
            long unavailable = agents.stream().filter(a -> a.getState() == AgentState.UNAVAILABLE).count();
            response.setMetadata(Map.of(
                    "online", online,
                    "onCall", onCall,
                    "away", away,
                    "unavailable", unavailable,
                    "total", (long) agents.size(),
                    "agentCount", (long) agents.size()
            ));

            return ResponseEntity.ok(response);
        } catch (RedisUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private AgentRow buildAgentRow(Agent agent, Call call) {
        return AgentRow.builder()
                .id(agent.getId())
                .name(agent.getName())
                .state(agent.getState())
                .stateChangedAt(agent.getStateChangedAt())
                .currentCallId(agent.getCurrentCallId())
                .currentCaller(call != null ? call.getOriginator() : null)
                .callStartTime(call != null ? call.getStartTime() : null)
                .callState(call != null ? call.getState() : null)
                .lastCallOriginator(agent.getLastCallOriginator())
                .lastCallStartTime(agent.getLastCallStartTime())
                .lastCallEndTime(agent.getLastCallEndTime())
                .lastCallDurationSeconds(agent.getLastCallDurationSeconds())
                .build();
    }

    /**
     * Filter a computed duration column. Converts the filter's seconds value
     * to a timestamp comparison against the given reference instant.
     */
    private boolean matchesDurationFilter(Instant timestamp, Instant now, GridRequest.FilterItem filter) {
        if (timestamp == null || filter.getFilter() == null) {
            return timestamp == null && "equals".equals(filter.getType()) && "0".equals(filter.getFilter());
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
