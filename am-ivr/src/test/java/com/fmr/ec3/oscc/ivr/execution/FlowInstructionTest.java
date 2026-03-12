package com.fmr.ec3.oscc.ivr.execution;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowInstructionTest {

    @Test
    void buildWithTypeAndParameters() {
        Map<String, Object> params = Map.of(
                "prompt", "Say your name",
                "language", "en-US",
                "timeoutSeconds", 5,
                "bargeIn", true);

        FlowInstruction instruction = FlowInstruction.builder()
                .type(InstructionType.CAPTURE_INPUT)
                .parameters(params)
                .build();

        assertThat(instruction.getType()).isEqualTo(InstructionType.CAPTURE_INPUT);
        assertThat(instruction.getParameters())
                .containsEntry("prompt", "Say your name")
                .containsEntry("language", "en-US")
                .containsEntry("timeoutSeconds", 5)
                .containsEntry("bargeIn", true);
    }

    @Test
    void nullTypeThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> FlowInstruction.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void emptyParametersDefaultsToEmptyMap() {
        FlowInstruction instruction = FlowInstruction.builder()
                .type(InstructionType.CAPTURE_INPUT)
                .build();

        assertThat(instruction.getParameters()).isEmpty();
    }

    @Test
    void parametersMapIsUnmodifiable() {
        FlowInstruction instruction = FlowInstruction.builder()
                .type(InstructionType.CAPTURE_INPUT)
                .parameters(Map.of("key", "value"))
                .build();

        assertThatThrownBy(() -> instruction.getParameters().put("new", "entry"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
