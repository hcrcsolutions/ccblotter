package com.fmr.ec3.oscc.ivr.server.service;

import com.fmr.ec3.oscc.ivr.IvrFlowException;
import com.fmr.ec3.oscc.ivr.execution.CapturedInput;
import com.fmr.ec3.oscc.ivr.execution.ExecutionResult;
import com.fmr.ec3.oscc.ivr.execution.FlowInstruction;
import com.fmr.ec3.oscc.ivr.execution.HttpNodeExecutor;
import com.fmr.ec3.oscc.ivr.execution.SessionContext;
import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.server.dto.ExecuteNodeRequest;
import com.fmr.ec3.oscc.ivr.server.dto.ExecuteNodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(NodeExecutionService.class);

    private final FlowCacheService flowCacheService;
    private final HttpNodeExecutor httpNodeExecutor = new HttpNodeExecutor();

    public NodeExecutionService(FlowCacheService flowCacheService) {
        this.flowCacheService = flowCacheService;
    }

    public ExecuteNodeResult executeNode(ExecuteNodeRequest request) {
        FlowDefinition flow = flowCacheService.get(request.getFlowId());

        FlowNode node = flow.getNodes().stream()
                .filter(n -> request.getNodeId().equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new IvrFlowException(
                        "Node not found: " + request.getNodeId()));

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
            case NLU_INTENT:
                throw new IvrFlowException(
                        "Unsupported node type in ivr-server: " + node.getType());
            default:
                throw new IvrFlowException(
                        "Unsupported dynamic node type: " + node.getType());
        }

        log.debug("Executed node {} in flow {}", request.getNodeId(), request.getFlowId());

        ExecuteNodeResult.Builder dtoBuilder = ExecuteNodeResult.builder()
                .result(result.getResult())
                .updatedVariables(result.getUpdatedVariables());

        FlowInstruction instruction = result.getInstruction();
        if (instruction != null) {
            dtoBuilder.instructionType(instruction.getType().name())
                    .instructionParameters(instruction.getParameters());
        }

        return dtoBuilder.build();
    }
}
