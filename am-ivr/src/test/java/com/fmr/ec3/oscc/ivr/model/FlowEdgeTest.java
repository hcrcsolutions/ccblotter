package com.fmr.ec3.oscc.ivr.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowEdgeTest {

    @Test
    void buildMinimalEdge() {
        FlowEdge edge = FlowEdge.builder()
                .id("e1")
                .sourceNodeId("n1")
                .targetNodeId("n2")
                .build();

        assertThat(edge.getId()).isEqualTo("e1");
        assertThat(edge.getSourceNodeId()).isEqualTo("n1");
        assertThat(edge.getTargetNodeId()).isEqualTo("n2");
        assertThat(edge.getCondition()).isNull();
    }

    @Test
    void buildEdgeWithCondition() {
        FlowEdge edge = FlowEdge.builder()
                .id("e1")
                .sourceNodeId("n1")
                .targetNodeId("n2")
                .condition("timeout")
                .build();

        assertThat(edge.getCondition()).isEqualTo("timeout");
    }

    @Test
    void idIsRequired() {
        assertThatThrownBy(() -> FlowEdge.builder()
                .sourceNodeId("n1")
                .targetNodeId("n2")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id is required");
    }

    @Test
    void sourceNodeIdIsRequired() {
        assertThatThrownBy(() -> FlowEdge.builder()
                .id("e1")
                .targetNodeId("n2")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceNodeId is required");
    }

    @Test
    void targetNodeIdIsRequired() {
        assertThatThrownBy(() -> FlowEdge.builder()
                .id("e1")
                .sourceNodeId("n1")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetNodeId is required");
    }
}
