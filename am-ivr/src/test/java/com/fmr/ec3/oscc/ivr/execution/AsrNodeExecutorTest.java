package com.fmr.ec3.oscc.ivr.execution;

import com.fmr.ec3.oscc.ivr.IvrFlowException;
import com.fmr.ec3.oscc.ivr.ai.AsrEngine;
import com.fmr.ec3.oscc.ivr.ai.AsrResult;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.NodeType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsrNodeExecutorTest {

    private final AsrEngine asrEngine = mock(AsrEngine.class);
    private final AsrNodeExecutor executor = new AsrNodeExecutor(asrEngine);

    @Test
    void phaseOneReturnsCaptureInputInstruction() {
        Map<String, Object> config = new HashMap<>();
        config.put("prompt", "Please say your account number");
        config.put("language", "en-US");
        config.put("timeoutSeconds", 10);
        config.put("bargeIn", false);
        config.put("resultVariable", "transcript");

        FlowNode node = FlowNode.builder()
                .id("asr-1")
                .type(NodeType.ASR_COLLECT)
                .config(config)
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-1")
                .sessionStartTime(Instant.now())
                .build();

        ExecutionResult result = executor.execute(node, ctx);

        assertThat(result.getInstruction()).isNotNull();
        assertThat(result.getInstruction().getType()).isEqualTo(InstructionType.CAPTURE_INPUT);
        assertThat(result.getInstruction().getParameters())
                .containsEntry("prompt", "Please say your account number")
                .containsEntry("language", "en-US")
                .containsEntry("timeoutSeconds", 10)
                .containsEntry("bargeIn", false);
        assertThat(result.getResult()).isEmpty();
        assertThat(result.getUpdatedVariables()).isEmpty();
    }

    @Test
    void phaseTwoCallsAsrEngineAndUpdatesVariables() {
        when(asrEngine.transcribe(eq("audioBase64=="), eq("en-US")))
                .thenReturn(new AsrResult("my account is twelve thirty four", 0.92));

        Map<String, Object> config = new HashMap<>();
        config.put("language", "en-US");
        config.put("resultVariable", "spokenText");

        FlowNode node = FlowNode.builder()
                .id("asr-2")
                .type(NodeType.ASR_COLLECT)
                .config(config)
                .build();

        CapturedInput captured = CapturedInput.builder()
                .audioData("audioBase64==")
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-2")
                .variables(Map.of("existingVar", "keep"))
                .capturedInput(captured)
                .sessionStartTime(Instant.now())
                .build();

        ExecutionResult result = executor.execute(node, ctx);

        verify(asrEngine).transcribe("audioBase64==", "en-US");
        assertThat(result.getInstruction()).isNull();
        assertThat(result.getResult())
                .containsEntry("transcript", "my account is twelve thirty four")
                .containsEntry("confidence", 0.92);
        assertThat(result.getUpdatedVariables())
                .containsEntry("spokenText", "my account is twelve thirty four")
                .containsEntry("existingVar", "keep");
    }

    @Test
    void missingResultVariableThrowsIvrFlowException() {
        Map<String, Object> config = new HashMap<>();
        config.put("language", "en-US");

        FlowNode node = FlowNode.builder()
                .id("asr-3")
                .type(NodeType.ASR_COLLECT)
                .config(config)
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-3")
                .sessionStartTime(Instant.now())
                .build();

        assertThatThrownBy(() -> executor.execute(node, ctx))
                .isInstanceOf(IvrFlowException.class)
                .hasMessageContaining("resultVariable is required");
    }

    @Test
    void phaseOneUsesDefaults() {
        Map<String, Object> config = new HashMap<>();
        config.put("resultVariable", "transcript");

        FlowNode node = FlowNode.builder()
                .id("asr-4")
                .type(NodeType.ASR_COLLECT)
                .config(config)
                .build();

        SessionContext ctx = SessionContext.builder()
                .callId("call-4")
                .sessionStartTime(Instant.now())
                .build();

        ExecutionResult result = executor.execute(node, ctx);

        assertThat(result.getInstruction().getParameters())
                .containsEntry("prompt", "")
                .containsEntry("language", "en-US")
                .containsEntry("timeoutSeconds", 5)
                .containsEntry("bargeIn", true);
    }
}
