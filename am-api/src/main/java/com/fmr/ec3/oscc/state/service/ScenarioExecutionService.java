package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.dto.response.ScenarioContentDto;
import com.fmr.ec3.oscc.state.dto.response.ScenarioRunDto;
import com.fmr.ec3.oscc.state.entity.TestScenarioEntity;
import com.fmr.ec3.oscc.state.entity.TestScenarioRunEntity;
import com.fmr.ec3.oscc.state.exception.TestScenarioNotFoundException;
import com.fmr.ec3.oscc.state.model.Agent;
import com.fmr.ec3.oscc.state.model.AgentState;
import com.fmr.ec3.oscc.state.model.Call;
import com.fmr.ec3.oscc.state.model.CallState;
import com.fmr.ec3.oscc.state.model.EdgeType;
import com.fmr.ec3.oscc.state.model.ScenarioAction;
import com.fmr.ec3.oscc.state.repository.TestScenarioRepository;
import com.fmr.ec3.oscc.state.repository.TestScenarioRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioExecutionService {

    private final TestScenarioRepository scenarioRepository;
    private final TestScenarioRunRepository runRepository;
    private final TestScenarioService scenarioService;
    private final AgentService agentService;
    private final CallService callService;
    private final QueueService queueService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Transactional
    public ScenarioRunDto startExecution(UUID scenarioId, String timingMode) {
        TestScenarioEntity scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new TestScenarioNotFoundException(scenarioId));

        TestScenarioRunEntity run = TestScenarioRunEntity.builder()
                .scenarioId(scenarioId)
                .scenarioVersion(scenario.getVersion())
                .status("PENDING")
                .timingMode(timingMode != null ? timingMode : "COMPRESSED")
                .build();

        run = runRepository.save(run);
        final UUID runId = run.getId();
        final int scenarioVersion = scenario.getVersion();

        executor.submit(() -> executeScenario(runId, scenarioId, scenarioVersion, timingMode));

        return toRunDto(run);
    }

    private void executeScenario(UUID runId, UUID scenarioId, int scenarioVersion, String timingMode) {
        List<Map<String, Object>> transcript = new ArrayList<>();
        List<Map<String, Object>> assertionResults = new ArrayList<>();
        Set<String> createdAgentIds = new HashSet<>();
        Set<String> createdCallIds = new HashSet<>();
        Set<String> createdQueueIds = new HashSet<>();

        try {
            // Update status to RUNNING
            updateRunStatus(runId, "RUNNING", null, null);
            updateRunStartedAt(runId);

            // Load content
            ScenarioContentDto contentDto = scenarioService.getContent(scenarioId);
            Map<String, Object> content = objectMapper.readValue(contentDto.getContent(), Map.class);

            List<Map<String, Object>> actors = (List<Map<String, Object>>) content.getOrDefault("actors", List.of());
            List<Map<String, Object>> steps = (List<Map<String, Object>>) content.getOrDefault("steps", List.of());
            List<Map<String, Object>> assertions = (List<Map<String, Object>>) content.getOrDefault("assertions", List.of());
            Map<String, Object> settings = (Map<String, Object>) content.getOrDefault("settings", Map.of());

            double timeScale = getDouble(settings, "compressedTimeScale", 10.0);
            boolean compressed = "COMPRESSED".equals(timingMode);
            boolean cleanup = getBoolean(settings, "cleanupAfterRun", true);

            // Build actor lookup
            Map<String, Map<String, Object>> actorMap = new HashMap<>();
            for (Map<String, Object> actor : actors) {
                actorMap.put((String) actor.get("id"), actor);
            }

            // Build step lookup
            Map<String, Map<String, Object>> stepMap = new LinkedHashMap<>();
            for (Map<String, Object> step : steps) {
                stepMap.put((String) step.get("id"), step);
            }

            // Parse dependsOn with backward compatibility (string or {stepId, type})
            Map<String, List<String>> stepDepIds = new HashMap<>();
            Map<String, Map<String, EdgeType>> stepDepTypes = new HashMap<>();
            for (Map<String, Object> step : steps) {
                String stepId = (String) step.get("id");
                List<String> depIds = new ArrayList<>();
                Map<String, EdgeType> depTypeMap = new HashMap<>();
                Object rawDeps = step.get("dependsOn");
                if (rawDeps instanceof List<?> depList) {
                    for (Object dep : depList) {
                        if (dep instanceof String depStr) {
                            depIds.add(depStr);
                            depTypeMap.put(depStr, EdgeType.SEQUENCE);
                        } else if (dep instanceof Map<?, ?> depMap) {
                            String depStepId = (String) depMap.get("stepId");
                            EdgeType depType = parseEdgeType(depMap.get("type"));
                            if (depStepId != null) {
                                depIds.add(depStepId);
                                depTypeMap.put(depStepId, depType);
                            }
                        }
                    }
                }
                stepDepIds.put(stepId, depIds);
                stepDepTypes.put(stepId, depTypeMap);
            }

            // Shared step results for SYNC data injection
            ConcurrentHashMap<String, Map<String, Object>> stepResults =
                    new ConcurrentHashMap<>();

            // Topological sort via Kahn's algorithm
            Map<String, Integer> inDegree = new HashMap<>();
            Map<String, List<String>> dependents = new HashMap<>();
            for (Map<String, Object> step : steps) {
                String stepId = (String) step.get("id");
                List<String> deps = stepDepIds.getOrDefault(stepId, List.of());
                inDegree.put(stepId, deps.size());
                for (String depId : deps) {
                    dependents.computeIfAbsent(depId, k -> new ArrayList<>()).add(stepId);
                }
            }

            Queue<String> ready = new LinkedList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    ready.add(entry.getKey());
                }
            }

            // Execute steps concurrently by dependency level
            Map<String, CompletableFuture<Void>> stepFutures = new HashMap<>();
            List<Map<String, Object>> syncTranscript = Collections.synchronizedList(transcript);
            List<Map<String, Object>> syncAssertionResults = Collections.synchronizedList(assertionResults);
            int processedCount = 0;

            while (!ready.isEmpty()) {
                // Launch all ready steps concurrently
                List<String> batch = new ArrayList<>();
                while (!ready.isEmpty()) {
                    batch.add(ready.poll());
                }

                List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
                for (String stepId : batch) {
                    Map<String, Object> step = stepMap.get(stepId);
                    String actorId = (String) step.get("actorId");
                    ScenarioAction action = parseScenarioAction(
                            (String) step.get("action"));
                    // Read delayMs with fallback to timeOffsetMs for backward compat
                    long stepDelayMs = getLong(step, "delayMs",
                            getLong(step, "timeOffsetMs", 0));
                    Map<String, Object> stepConfig =
                            (Map<String, Object>) step.getOrDefault("config", Map.of());
                    List<String> depIds = stepDepIds.getOrDefault(stepId, List.of());
                    Map<String, EdgeType> depTypes = stepDepTypes.getOrDefault(
                            stepId, Map.of());

                    Map<String, Object> actor = actorMap.get(actorId);
                    String actorName = actor != null ? (String) actor.get("name") : "Unknown";

                    // Wait for dependency futures before starting
                    List<CompletableFuture<Void>> depFutures = new ArrayList<>();
                    for (String depId : depIds) {
                        CompletableFuture<Void> dep = stepFutures.get(depId);
                        if (dep != null) {
                            depFutures.add(dep);
                        }
                    }

                    CompletableFuture<Void> future = CompletableFuture.allOf(
                            depFutures.toArray(new CompletableFuture[0])
                    ).thenRunAsync(() -> {
                        try {
                            // Log per-type waiting messages
                            for (String depId : depIds) {
                                EdgeType depType = depTypes.getOrDefault(
                                        depId, EdgeType.SEQUENCE);
                                if (depType == EdgeType.WAIT_FOR) {
                                    log.info("Step {} waiting for condition from step {}",
                                            stepId, depId);
                                }
                            }

                            // SYNC: inject source step result data into step config
                            Map<String, Object> effectiveConfig = new HashMap<>(stepConfig);
                            for (String depId : depIds) {
                                EdgeType depType = depTypes.getOrDefault(
                                        depId, EdgeType.SEQUENCE);
                                if (depType == EdgeType.SYNC) {
                                    Map<String, Object> srcResult = stepResults.get(depId);
                                    if (srcResult != null) {
                                        effectiveConfig.putAll(srcResult);
                                        log.info("SYNC: injected data from step {} into step {}",
                                                depId, stepId);
                                    }
                                }
                            }

                            // Per-step delay
                            if (stepDelayMs > 0) {
                                long actualDelay = compressed
                                        ? (long) (stepDelayMs / timeScale)
                                        : stepDelayMs;
                                if (actualDelay > 0) {
                                    Thread.sleep(actualDelay);
                                }
                            }

                            Instant stepStart = Instant.now();
                            String status = "SUCCESS";
                            String detail = "";

                            try {
                                detail = executeStep(action, actorId, actorName,
                                        effectiveConfig, actor, createdAgentIds,
                                        createdCallIds, createdQueueIds);

                                // Store step result for downstream SYNC edges
                                Map<String, Object> resultData = new HashMap<>();
                                resultData.put("detail", detail);
                                resultData.put("stepId", stepId);
                                resultData.put("action", action.name());
                                stepResults.put(stepId, resultData);
                            } catch (Exception e) {
                                status = "ERROR";
                                detail = e.getMessage();
                                log.warn("Step {} ({}) failed: {}", stepId, action,
                                        e.getMessage());
                            }

                            long durationMs = Instant.now().toEpochMilli()
                                    - stepStart.toEpochMilli();

                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("timestamp", Instant.now().toString());
                            entry.put("actorId", actorId);
                            entry.put("actorName", actorName);
                            entry.put("stepId", stepId);
                            entry.put("action", action.name());
                            entry.put("status", status);
                            entry.put("detail", detail);
                            entry.put("durationMs", durationMs);
                            syncTranscript.add(entry);

                            for (Map<String, Object> assertion : assertions) {
                                if (stepId.equals(assertion.get("afterStepId"))) {
                                    Map<String, Object> result = evaluateAssertion(
                                            assertion, actorMap, createdAgentIds);
                                    syncAssertionResults.add(result);
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Step interrupted: " + stepId, e);
                        }
                    }, executor);

                    stepFutures.put(stepId, future);
                    batchFutures.add(future);
                }

                // Wait for current batch to complete
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                        .get(60, TimeUnit.SECONDS);
                processedCount += batch.size();

                // Decrease in-degree and discover newly ready steps
                for (String completedId : batch) {
                    for (String depStepId : dependents.getOrDefault(completedId, List.of())) {
                        int newDegree = inDegree.merge(depStepId, -1, Integer::sum);
                        if (newDegree == 0) {
                            ready.add(depStepId);
                        }
                    }
                }
            }

            // Detect cycles: if not all steps were processed, there's a cycle
            if (processedCount < steps.size()) {
                throw new IllegalStateException(
                        "Dependency cycle detected — unable to execute all steps");
            }

            // Determine result
            boolean allPassed = assertionResults.stream()
                    .allMatch(r -> Boolean.TRUE.equals(r.get("passed")));
            String result = assertionResults.isEmpty() ? "PASS" : (allPassed ? "PASS" : "FAIL");

            // Cleanup if requested
            if (cleanup) {
                performCleanup(createdAgentIds, createdCallIds, createdQueueIds);
            }

            // Finalize run
            finalizeRun(runId, "COMPLETED", result, transcript, assertionResults, null);

        } catch (Exception e) {
            log.error("Scenario execution failed for run {}", runId, e);
            finalizeRun(runId, "FAILED", "ERROR", transcript, assertionResults, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String executeStep(
            ScenarioAction action, String actorId, String actorName,
            Map<String, Object> config, Map<String, Object> actor,
            Set<String> createdAgentIds, Set<String> createdCallIds,
            Set<String> createdQueueIds) {

        String scenarioAgentId = "test-" + actorId;
        Map<String, Object> actorConfig = actor != null
                ? (Map<String, Object>) actor.getOrDefault("config", Map.of())
                : Map.of();

        return switch (action) {
            case LOGIN -> {
                String agentDisplayName = actorConfig.containsKey("agentName")
                        ? (String) actorConfig.get("agentName")
                        : actorName;
                Agent agent = Agent.builder()
                        .id(scenarioAgentId)
                        .name(agentDisplayName)
                        .state(AgentState.UNAVAILABLE)
                        .stateChangedAt(Instant.now())
                        .build();
                agentService.saveAgent(agent);
                createdAgentIds.add(scenarioAgentId);
                yield "Agent logged in: " + agentDisplayName;
            }
            case GO_ONLINE -> {
                agentService.updateAgentState(scenarioAgentId, AgentState.ONLINE, null);
                yield "Agent went online";
            }
            case GO_AWAY -> {
                agentService.updateAgentState(scenarioAgentId, AgentState.AWAY, null);
                yield "Agent went away";
            }
            case ANSWER_CALL -> {
                var queuedCall = queueService.peekNextCall();
                if (queuedCall != null) {
                    queueService.removeFromQueue(queuedCall.getId());
                    Call call = callService.startCall(queuedCall.getOriginator(), scenarioAgentId);
                    agentService.updateAgentState(scenarioAgentId, AgentState.ON_CALL, call.getId());
                    createdCallIds.add(call.getId());
                    yield "Answered call from " + queuedCall.getOriginator() + ", callId=" + call.getId();
                }
                yield "No call in queue to answer";
            }
            case HOLD_CALL -> {
                Agent heldAgent = agentService.getAgent(scenarioAgentId);
                if (heldAgent != null && heldAgent.getCurrentCallId() != null) {
                    callService.setCallState(heldAgent.getCurrentCallId(), CallState.ON_HOLD);
                    yield "Call placed on hold";
                }
                yield "No active call to hold";
            }
            case RESUME_CALL -> {
                Agent resumeAgent = agentService.getAgent(scenarioAgentId);
                if (resumeAgent != null && resumeAgent.getCurrentCallId() != null) {
                    callService.setCallState(resumeAgent.getCurrentCallId(), CallState.TALKING);
                    yield "Call resumed";
                }
                yield "No held call to resume";
            }
            case END_CALL -> {
                Agent endAgent = agentService.getAgent(scenarioAgentId);
                if (endAgent != null && endAgent.getCurrentCallId() != null) {
                    String callId = endAgent.getCurrentCallId();
                    callService.endCall(callId);
                    agentService.updateAgentState(scenarioAgentId, AgentState.ONLINE, null);
                    yield "Call ended: " + callId;
                }
                yield "No active call to end";
            }
            case LOGOUT -> {
                agentService.updateAgentState(scenarioAgentId, AgentState.UNAVAILABLE, null);
                yield "Agent logged out";
            }
            case DIAL_IN -> {
                String originator = actorConfig.containsKey("phoneNumber")
                        ? (String) actorConfig.get("phoneNumber")
                        : "caller-" + actorId;
                var queued = queueService.addToQueue(originator, "default", 5);
                createdQueueIds.add(queued.getId());
                yield "Caller dialed in, queued as " + queued.getId();
            }
            case IVR_INPUT -> {
                String input = config.containsKey("input") ? (String) config.get("input") : "1";
                yield "IVR input: " + input;
            }
            case WAIT_IN_QUEUE -> "Waiting in queue";
            case HANG_UP -> "Caller hung up";
            case MONITOR -> {
                String callId = config.containsKey("callId")
                        ? (String) config.get("callId") : null;
                if (callId == null) {
                    String detail = config.containsKey("detail")
                            ? (String) config.get("detail") : "";
                    int idx = detail.indexOf("callId=");
                    if (idx >= 0) {
                        callId = detail.substring(idx + 7);
                    }
                }
                yield "Supervisor started monitoring"
                        + (callId != null ? ", callId=" + callId : "");
            }
            case WHISPER -> {
                String whisperMessage = config.containsKey("whisperMessage")
                        ? (String) config.get("whisperMessage") : "(no message)";
                yield "Supervisor whisper to agent: " + whisperMessage;
            }
            case BARGE_IN -> "Supervisor barged in — all parties can hear";
            case END_MONITOR -> "Supervisor ended monitoring session";
            case WAIT -> "Waited";
        };
    }

    private Map<String, Object> evaluateAssertion(
            Map<String, Object> assertion,
            Map<String, Map<String, Object>> actorMap,
            Set<String> createdAgentIds) {

        String assertionId = (String) assertion.get("id");
        String type = (String) assertion.get("type");
        Map<String, Object> config = (Map<String, Object>) assertion.getOrDefault("config", Map.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assertionId", assertionId);
        result.put("type", type);

        try {
            switch (type) {
                case "AGENT_IN_STATE": {
                    String targetActorId = (String) config.get("actorId");
                    String expectedState = (String) config.get("expectedState");
                    String agentId = "test-" + targetActorId;
                    Agent agent = agentService.getAgent(agentId);
                    String actualState = agent != null ? agent.getState().name() : "NOT_FOUND";
                    boolean passed = expectedState != null && expectedState.equals(actualState);
                    result.put("passed", passed);
                    result.put("expected", expectedState);
                    result.put("actual", actualState);
                    result.put("message", passed ? "Agent is in expected state" : "Agent state mismatch");
                    break;
                }
                case "CALL_EXISTS": {
                    var calls = callService.getActiveCalls();
                    boolean found = !calls.isEmpty();
                    result.put("passed", found);
                    result.put("expected", "active call exists");
                    result.put("actual", found ? calls.size() + " active call(s)" : "no active calls");
                    result.put("message", found ? "Active call found" : "No active calls");
                    break;
                }
                case "QUEUE_LENGTH": {
                    int expected = config.containsKey("expectedLength")
                            ? ((Number) config.get("expectedLength")).intValue() : 0;
                    int actual = queueService.getQueueCount();
                    boolean passed = expected == actual;
                    result.put("passed", passed);
                    result.put("expected", String.valueOf(expected));
                    result.put("actual", String.valueOf(actual));
                    result.put("message", passed ? "Queue length matches" : "Queue length mismatch");
                    break;
                }
                default: {
                    result.put("passed", true);
                    result.put("expected", "N/A");
                    result.put("actual", "N/A");
                    result.put("message", "Assertion type not implemented: " + type);
                    break;
                }
            }
        } catch (Exception e) {
            result.put("passed", false);
            result.put("expected", "success");
            result.put("actual", "error");
            result.put("message", "Assertion evaluation failed: " + e.getMessage());
        }

        return result;
    }

    private void performCleanup(Set<String> agentIds, Set<String> callIds, Set<String> queueIds) {
        for (String callId : callIds) {
            try {
                callService.endCall(callId);
            } catch (Exception e) {
                log.debug("Cleanup: could not end call {}: {}", callId, e.getMessage());
            }
        }
        for (String agentId : agentIds) {
            try {
                agentService.removeAgent(agentId);
            } catch (Exception e) {
                log.debug("Cleanup: could not remove agent {}: {}", agentId, e.getMessage());
            }
        }
        for (String queueId : queueIds) {
            try {
                queueService.removeFromQueue(queueId);
            } catch (Exception e) {
                log.debug("Cleanup: could not remove queued call {}: {}", queueId, e.getMessage());
            }
        }
        log.info("Cleanup completed: {} agents, {} calls, {} queued calls",
                agentIds.size(), callIds.size(), queueIds.size());
    }

    private void updateRunStatus(UUID runId, String status, String result, String errorMessage) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(status);
            if (result != null) {
                run.setResult(result);
            }
            if (errorMessage != null) {
                run.setErrorMessage(errorMessage);
            }
            runRepository.save(run);
        });
    }

    private void updateRunStartedAt(UUID runId) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStartedAt(Instant.now());
            run.setStatus("RUNNING");
            runRepository.save(run);
        });
    }

    private void finalizeRun(UUID runId, String status, String result,
                             List<Map<String, Object>> transcript,
                             List<Map<String, Object>> assertionResults,
                             String errorMessage) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(status);
            run.setResult(result);
            run.setFinishedAt(Instant.now());
            run.setErrorMessage(errorMessage);
            try {
                run.setTranscript(objectMapper.writeValueAsString(transcript));
                run.setAssertionResults(objectMapper.writeValueAsString(assertionResults));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize run results", e);
            }
            runRepository.save(run);
        });
        log.info("Scenario run {} completed with result: {}", runId, result);
    }

    private ScenarioRunDto toRunDto(TestScenarioRunEntity entity) {
        return ScenarioRunDto.builder()
                .id(entity.getId())
                .scenarioId(entity.getScenarioId())
                .scenarioVersion(entity.getScenarioVersion())
                .status(entity.getStatus())
                .timingMode(entity.getTimingMode())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .result(entity.getResult())
                .transcript(entity.getTranscript())
                .assertionResults(entity.getAssertionResults())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return defaultValue;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return defaultValue;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return defaultValue;
    }

    private static ScenarioAction parseScenarioAction(String raw) {
        try {
            return ScenarioAction.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown scenario action: " + raw);
        }
    }

    private static EdgeType parseEdgeType(Object raw) {
        if (raw instanceof String name) {
            try {
                return EdgeType.valueOf(name);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown edge type '{}', defaulting to SEQUENCE", name);
            }
        }
        return EdgeType.SEQUENCE;
    }
}
