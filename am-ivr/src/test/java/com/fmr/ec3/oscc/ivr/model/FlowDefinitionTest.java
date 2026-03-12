package com.fmr.ec3.oscc.ivr.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowDefinitionTest {

    @Test
    void buildMinimalFlow() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Test Flow")
                .build();

        assertThat(flow.getName()).isEqualTo("Test Flow");
        assertThat(flow.getStatus()).isEqualTo(FlowStatus.DRAFT);
        assertThat(flow.getMaxSessionDurationSeconds()).isEqualTo(600);
        assertThat(flow.getMaxSteps()).isEqualTo(100);
        assertThat(flow.getNodes()).isEmpty();
        assertThat(flow.getEdges()).isEmpty();
        assertThat(flow.getVariables()).isEmpty();
    }

    @Test
    void buildFullFlow() {
        Instant now = Instant.now();
        FlowNode node = FlowNode.builder()
                .id("n1")
                .type(NodeType.PLAY_PROMPT)
                .build();
        FlowEdge edge = FlowEdge.builder()
                .id("e1")
                .sourceNodeId("n1")
                .targetNodeId("n2")
                .build();
        FlowVariable var = FlowVariable.builder()
                .name("caller_id")
                .type(VariableType.STRING)
                .build();

        FlowDefinition flow = FlowDefinition.builder()
                .id("flow-1")
                .name("Full Flow")
                .description("A full flow")
                .version(3)
                .status(FlowStatus.PUBLISHED)
                .entryNodeId("n1")
                .nodes(List.of(node))
                .edges(List.of(edge))
                .variables(List.of(var))
                .maxSessionDurationSeconds(300)
                .maxSteps(50)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(flow.getId()).isEqualTo("flow-1");
        assertThat(flow.getDescription()).isEqualTo("A full flow");
        assertThat(flow.getVersion()).isEqualTo(3);
        assertThat(flow.getStatus()).isEqualTo(FlowStatus.PUBLISHED);
        assertThat(flow.getEntryNodeId()).isEqualTo("n1");
        assertThat(flow.getNodes()).hasSize(1);
        assertThat(flow.getEdges()).hasSize(1);
        assertThat(flow.getVariables()).hasSize(1);
        assertThat(flow.getMaxSessionDurationSeconds()).isEqualTo(300);
        assertThat(flow.getMaxSteps()).isEqualTo(50);
        assertThat(flow.getCreatedAt()).isEqualTo(now);
        assertThat(flow.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void nameIsRequired() {
        assertThatThrownBy(() -> FlowDefinition.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> FlowDefinition.builder().name("  ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void nodesListIsImmutable() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Test")
                .nodes(List.of(FlowNode.builder().id("n1").type(NodeType.HANGUP).build()))
                .build();

        assertThatThrownBy(() -> flow.getNodes().add(
                FlowNode.builder().id("n2").type(NodeType.HANGUP).build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
