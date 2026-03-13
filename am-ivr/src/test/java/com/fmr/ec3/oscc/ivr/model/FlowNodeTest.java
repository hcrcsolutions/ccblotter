package com.fmr.ec3.oscc.ivr.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowNodeTest {

    @Test
    void buildMinimalNode() {
        FlowNode node = FlowNode.builder()
                .id("n1")
                .type(NodeType.PLAY_PROMPT)
                .build();

        assertThat(node.getId()).isEqualTo("n1");
        assertThat(node.getType()).isEqualTo(NodeType.PLAY_PROMPT);
        assertThat(node.getLabel()).isNull();
        assertThat(node.getConfig()).isEmpty();
    }

    @Test
    void buildFullNode() {
        Map<String, Object> config = Map.of("audioUrl", "http://example.com/audio.wav");

        FlowNode node = FlowNode.builder()
                .id("n1")
                .type(NodeType.PLAY_PROMPT)
                .label("Welcome")
                .config(config)
                .build();

        assertThat(node.getLabel()).isEqualTo("Welcome");
        assertThat(node.getConfig()).containsEntry("audioUrl", "http://example.com/audio.wav");
    }

    @Test
    void idIsRequired() {
        assertThatThrownBy(() -> FlowNode.builder().type(NodeType.HANGUP).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id is required");
    }

    @Test
    void typeIsRequired() {
        assertThatThrownBy(() -> FlowNode.builder().id("n1").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void configIsImmutable() {
        FlowNode node = FlowNode.builder()
                .id("n1")
                .type(NodeType.HANGUP)
                .config(Map.of("key", "value"))
                .build();

        assertThatThrownBy(() -> node.getConfig().put("extra", "nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
