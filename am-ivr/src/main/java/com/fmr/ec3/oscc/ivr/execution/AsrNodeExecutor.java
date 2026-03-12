package com.fmr.ec3.oscc.ivr.execution;

import com.fmr.ec3.oscc.ivr.IvrFlowException;
import com.fmr.ec3.oscc.ivr.ai.AsrEngine;
import com.fmr.ec3.oscc.ivr.ai.AsrResult;
import com.fmr.ec3.oscc.ivr.model.FlowNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes ASR_COLLECT nodes using a two-phase protocol.
 *
 * <p>Phase 1 (no captured input): returns a {@link FlowInstruction} with type
 * {@link InstructionType#CAPTURE_INPUT} telling the routing server to capture caller audio.
 *
 * <p>Phase 2 (captured input present): calls {@link AsrEngine#transcribe} and stores
 * the transcript in the configured result variable.
 */
public class AsrNodeExecutor implements DynamicNodeExecutor {

    private final AsrEngine asrEngine;

    public AsrNodeExecutor(AsrEngine asrEngine) {
        this.asrEngine = asrEngine;
    }

    @Override
    public ExecutionResult execute(FlowNode node, SessionContext context) {
        Map<String, Object> config = node.getConfig();

        String resultVariable = (String) config.get("resultVariable");
        if (resultVariable == null || resultVariable.isBlank()) {
            throw new IvrFlowException(
                    "resultVariable is required in ASR_COLLECT config for node " + node.getId());
        }

        if (context.getCapturedInput() == null) {
            return executePhaseOne(config);
        }
        return executePhaseTwo(node, context, config, resultVariable);
    }

    private ExecutionResult executePhaseOne(Map<String, Object> config) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("prompt", config.getOrDefault("prompt", ""));
        params.put("language", config.getOrDefault("language", "en-US"));

        Object timeoutObj = config.get("timeoutSeconds");
        int timeout = (timeoutObj instanceof Number)
                ? ((Number) timeoutObj).intValue()
                : 5;
        params.put("timeoutSeconds", timeout);

        Object bargeInObj = config.get("bargeIn");
        boolean bargeIn = (bargeInObj instanceof Boolean)
                ? (Boolean) bargeInObj
                : true;
        params.put("bargeIn", bargeIn);

        FlowInstruction instruction = FlowInstruction.builder()
                .type(InstructionType.CAPTURE_INPUT)
                .parameters(params)
                .build();

        return ExecutionResult.builder()
                .instruction(instruction)
                .build();
    }

    private ExecutionResult executePhaseTwo(FlowNode node, SessionContext context,
                                            Map<String, Object> config, String resultVariable) {
        CapturedInput captured = context.getCapturedInput();
        String language = (String) config.getOrDefault("language", "en-US");

        AsrResult asrResult = asrEngine.transcribe(captured.getAudioData(), language);

        Map<String, Object> result = new HashMap<>();
        result.put("transcript", asrResult.getTranscript());
        result.put("confidence", asrResult.getConfidence());

        Map<String, Object> updatedVars = new HashMap<>(context.getVariables());
        updatedVars.put(resultVariable, asrResult.getTranscript());

        return ExecutionResult.builder()
                .result(result)
                .updatedVariables(updatedVars)
                .build();
    }
}
