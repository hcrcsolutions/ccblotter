package com.example.agentmonitor.service;

import com.example.agentmonitor.dto.request.ExecuteNodeRequest;
import com.example.agentmonitor.dto.response.ExecuteNodeResultDto;
import com.example.agentmonitor.dto.response.FlowContentDto;
import com.example.agentmonitor.exception.IvrFlowNotFoundException;
import com.fmr.ec3.oscc.ivr.IvrFlowException;
import com.fmr.ec3.oscc.ivr.ai.AsrEngine;
import com.fmr.ec3.oscc.ivr.ai.NluEngine;
import com.fmr.ec3.oscc.ivr.ai.NluResult;
import com.fmr.ec3.oscc.ivr.execution.AsrNodeExecutor;
import com.fmr.ec3.oscc.ivr.execution.CapturedInput;
import com.fmr.ec3.oscc.ivr.execution.ExecutionResult;
import com.fmr.ec3.oscc.ivr.execution.FlowInstruction;
import com.fmr.ec3.oscc.ivr.execution.HttpNodeExecutor;
import com.fmr.ec3.oscc.ivr.execution.SessionContext;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.NodeType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class IvrExecutionService {

    private final IvrFlowService flowService;
    private final ObjectMapper objectMapper;
    private final HttpNodeExecutor httpNodeExecutor = new HttpNodeExecutor();
    private final AsrNodeExecutor asrNodeExecutor;
    private final NluEngine nluEngine;

    public IvrExecutionService(IvrFlowService flowService,
                               ObjectMapper objectMapper,
                               ObjectProvider<AsrEngine> asrEngineProvider,
                               ObjectProvider<NluEngine> nluEngineProvider) {
        this.flowService = flowService;
        this.objectMapper = objectMapper;
        this.asrNodeExecutor = new AsrNodeExecutor(asrEngineProvider.getIfAvailable());
        this.nluEngine = nluEngineProvider.getIfAvailable();
    }

    /**
     * Execute a dynamic node (HTTP_REQUEST, etc.) with the provided session context.
     */
    @SuppressWarnings("unchecked")
    public ExecuteNodeResultDto executeNode(ExecuteNodeRequest request) {
        UUID flowId = UUID.fromString(request.getFlowId());

        FlowContentDto content = flowService.getContent(flowId);
        FlowNode node = findNode(content, request.getNodeId());

        if (node == null) {
            throw new IvrFlowException("Node not found: " + request.getNodeId());
        }

        SessionContext.Builder ctxBuilder = SessionContext.builder()
                .callId(request.getCallId())
                .originator(request.getOriginator())
                .variables(request.getVariables())
                .stepCount(request.getStepCount())
                .sessionStartTime(Instant.now());

        if (request.getAudioData() != null) {
            ctxBuilder.capturedInput(CapturedInput.builder()
                    .audioData(request.getAudioData())
                    .audioFormat(request.getAudioFormat() != null
                            ? request.getAudioFormat() : "wav")
                    .sampleRate(request.getSampleRate() != null
                            ? request.getSampleRate() : 16000)
                    .build());
        }

        SessionContext context = ctxBuilder.build();

        ExecutionResult result;
        switch (node.getType()) {
            case HTTP_REQUEST:
                result = httpNodeExecutor.execute(node, context);
                break;
            case ASR_COLLECT:
                result = asrNodeExecutor.execute(node, context);
                break;
            case NLU_INTENT:
                result = executeNluIntent(node, context);
                break;
            default:
                throw new IvrFlowException("Unsupported dynamic node type: " + node.getType());
        }

        log.debug("Executed node {} in flow {}", request.getNodeId(), request.getFlowId());

        ExecuteNodeResultDto.ExecuteNodeResultDtoBuilder dtoBuilder = ExecuteNodeResultDto.builder()
                .result(result.getResult())
                .updatedVariables(result.getUpdatedVariables());

        FlowInstruction instruction = result.getInstruction();
        if (instruction != null) {
            dtoBuilder.instructionType(instruction.getType().name())
                    .instructionParameters(instruction.getParameters());
        }

        return dtoBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private ExecutionResult executeNluIntent(FlowNode node, SessionContext context) {
        if (nluEngine == null) {
            throw new IvrFlowException("NluEngine is not configured");
        }

        Map<String, Object> config = node.getConfig();

        String inputVariable = (String) config.get("inputVariable");
        if (inputVariable == null || inputVariable.isBlank()) {
            throw new IvrFlowException(
                    "inputVariable is required in NLU_INTENT config for node " + node.getId());
        }

        String resultVariable = (String) config.get("resultVariable");
        if (resultVariable == null || resultVariable.isBlank()) {
            throw new IvrFlowException(
                    "resultVariable is required in NLU_INTENT config for node " + node.getId());
        }

        Object inputText = context.getVariables().get(inputVariable);
        if (inputText == null) {
            throw new IvrFlowException(
                    "Variable '" + inputVariable + "' not found in session for node " + node.getId());
        }

        List<String> expectedIntents = config.get("expectedIntents") instanceof List
                ? (List<String>) config.get("expectedIntents")
                : List.of();

        NluResult nluResult = nluEngine.classify(String.valueOf(inputText), expectedIntents);

        Map<String, Object> result = new HashMap<>();
        result.put("intent", nluResult.getIntent());
        result.put("confidence", nluResult.getConfidence());
        result.put("entities", nluResult.getEntities());

        Map<String, Object> updatedVars = new HashMap<>(context.getVariables());
        updatedVars.put(resultVariable, nluResult.getIntent());

        return ExecutionResult.builder()
                .result(result)
                .updatedVariables(updatedVars)
                .build();
    }

    @SuppressWarnings("unchecked")
    private FlowNode findNode(FlowContentDto content, String nodeId) {
        try {
            Map<String, Object> contentMap = objectMapper.readValue(content.getContent(), Map.class);
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) contentMap.getOrDefault("nodes", List.of());

            return nodes.stream()
                    .filter(n -> nodeId.equals(n.get("id")))
                    .findFirst()
                    .map(this::parseNode)
                    .orElse(null);
        } catch (JsonProcessingException e) {
            throw new IvrFlowException("Failed to parse flow content", e);
        }
    }

    @SuppressWarnings("unchecked")
    private FlowNode parseNode(Map<String, Object> raw) {
        Map<String, Object> config = (Map<String, Object>) raw.getOrDefault("config", Map.of());
        return FlowNode.builder()
                .id((String) raw.get("id"))
                .type(NodeType.valueOf((String) raw.get("type")))
                .label((String) raw.get("label"))
                .config(config)
                .build();
    }
}
