package com.fmr.ec3.oscc.ivr.validation;

import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.model.FlowEdge;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.FlowVariable;
import com.fmr.ec3.oscc.ivr.model.NodeType;

import com.fmr.ec3.oscc.ivr.model.VariableType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowValidatorTest {

    private FlowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FlowValidator();
    }

    @Test
    void validSimpleFlow() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Simple")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.PLAY_PROMPT),
                        node("n2", NodeType.HANGUP)))
                .edges(List.of(edge("e1", "n1", "n2", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void missingEntryNode() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("No Entry")
                .nodes(List.of(node("n1", NodeType.PLAY_PROMPT)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("MISSING_ENTRY_NODE"));
    }

    @Test
    void entryNodeNotFound() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Bad Entry")
                .entryNodeId("missing")
                .nodes(List.of(node("n1", NodeType.PLAY_PROMPT)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("ENTRY_NODE_NOT_FOUND"));
    }

    @Test
    void danglingEdgeTarget() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Dangling")
                .entryNodeId("n1")
                .nodes(List.of(node("n1", NodeType.PLAY_PROMPT)))
                .edges(List.of(edge("e1", "n1", "n999", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("DANGLING_EDGE_TARGET"));
    }

    @Test
    void menuMissingTimeoutAndInvalidEdges() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Menu Test")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.MENU),
                        node("n2", NodeType.HANGUP)))
                .edges(List.of(edge("e1", "n1", "n2", "1")))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .anyMatch(i -> i.getCode().equals("MISSING_TIMEOUT_EDGE"))
                .anyMatch(i -> i.getCode().equals("MISSING_INVALID_EDGE"));
    }

    @Test
    void menuWithTimeoutAndInvalidEdges() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Menu OK")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.MENU),
                        node("n2", NodeType.HANGUP),
                        node("n3", NodeType.PLAY_PROMPT)))
                .edges(List.of(
                        edge("e1", "n1", "n2", "1"),
                        edge("e2", "n1", "n3", "timeout"),
                        edge("e3", "n1", "n3", "invalid"),
                        edge("e4", "n3", "n1", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.getErrors()
                .stream()
                .filter(i -> i.getCode().equals("MISSING_TIMEOUT_EDGE")
                        || i.getCode().equals("MISSING_INVALID_EDGE"))
                .toList()).isEmpty();
    }

    @Test
    void httpTimeoutExceeded() {
        FlowNode httpNode = FlowNode.builder()
                .id("n2")
                .type(NodeType.HTTP_REQUEST)
                .config(Map.of("url", "http://example.com", "timeoutSeconds", 60))
                .build();

        FlowDefinition flow = FlowDefinition.builder()
                .name("HTTP Timeout")
                .entryNodeId("n1")
                .nodes(List.of(node("n1", NodeType.PLAY_PROMPT), httpNode))
                .edges(List.of(edge("e1", "n1", "n2", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("HTTP_TIMEOUT_EXCEEDED"));
    }

    @Test
    void conditionReferencesUndeclaredVariable() {
        FlowNode condNode = FlowNode.builder()
                .id("n2")
                .type(NodeType.CONDITION)
                .config(Map.of("variableName", "unknown_var", "operator", "EQ", "value", "x"))
                .build();

        FlowDefinition flow = FlowDefinition.builder()
                .name("Undeclared Var")
                .entryNodeId("n1")
                .nodes(List.of(node("n1", NodeType.PLAY_PROMPT), condNode))
                .edges(List.of(edge("e1", "n1", "n2", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("UNDECLARED_VARIABLE"));
    }

    @Test
    void conditionWithDeclaredVariable() {
        FlowNode condNode = FlowNode.builder()
                .id("n2")
                .type(NodeType.CONDITION)
                .config(Map.of("variableName", "status", "operator", "EQ", "value", "active"))
                .build();

        FlowVariable var = FlowVariable.builder()
                .name("status")
                .type(VariableType.STRING)
                .build();

        FlowDefinition flow = FlowDefinition.builder()
                .name("Declared Var")
                .entryNodeId("n1")
                .nodes(List.of(node("n1", NodeType.PLAY_PROMPT), condNode))
                .edges(List.of(edge("e1", "n1", "n2", null)))
                .variables(List.of(var))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.getErrors()
                .stream()
                .filter(i -> i.getCode().equals("UNDECLARED_VARIABLE"))
                .toList()).isEmpty();
    }

    @Test
    void invalidMaxSteps() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Bad Steps")
                .entryNodeId("n1")
                .maxSteps(0)
                .nodes(List.of(node("n1", NodeType.HANGUP)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("INVALID_MAX_STEPS"));
    }

    @Test
    void duplicateNodeIds() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Dupe Nodes")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.PLAY_PROMPT),
                        node("n1", NodeType.HANGUP)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("DUPLICATE_NODE_ID"));
    }

    @Test
    void duplicateEdgeIds() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Dupe Edges")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.PLAY_PROMPT),
                        node("n2", NodeType.HANGUP),
                        node("n3", NodeType.HANGUP)))
                .edges(List.of(
                        edge("e1", "n1", "n2", null),
                        edge("e1", "n1", "n3", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("DUPLICATE_EDGE_ID"));
    }

    @Test
    void orphanedNodeWarning() {
        FlowDefinition flow = FlowDefinition.builder()
                .name("Orphan")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.PLAY_PROMPT),
                        node("n2", NodeType.HANGUP),
                        node("orphan", NodeType.PLAY_PROMPT)))
                .edges(List.of(edge("e1", "n1", "n2", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings()).anyMatch(
                i -> i.getCode().equals("ORPHANED_NODE") && i.getNodeId().equals("orphan"));
    }

    @Test
    void infiniteLoopDetected() {
        // Cycle: n1 -> n2 -> n3 -> n1, all are SET_VARIABLE (no input/terminal)
        FlowDefinition flow = FlowDefinition.builder()
                .name("Loop")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.SET_VARIABLE),
                        node("n2", NodeType.SET_VARIABLE),
                        node("n3", NodeType.SET_VARIABLE)))
                .edges(List.of(
                        edge("e1", "n1", "n2", null),
                        edge("e2", "n2", "n3", null),
                        edge("e3", "n3", "n1", null)))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(i -> i.getCode().equals("INFINITE_LOOP"));
    }

    @Test
    void safeCycleWithInputNode() {
        // Cycle: n1 (MENU) -> n2 -> n1 is safe because MENU is an input node
        FlowDefinition flow = FlowDefinition.builder()
                .name("Safe Loop")
                .entryNodeId("n1")
                .nodes(List.of(
                        node("n1", NodeType.MENU),
                        node("n2", NodeType.PLAY_PROMPT),
                        node("n3", NodeType.HANGUP)))
                .edges(List.of(
                        edge("e1", "n1", "n2", "1"),
                        edge("e2", "n2", "n1", null),
                        edge("e3", "n1", "n3", "timeout"),
                        edge("e4", "n1", "n3", "invalid")))
                .build();

        ValidationResult result = validator.validate(flow);
        assertThat(result.getErrors()
                .stream()
                .filter(i -> i.getCode().equals("INFINITE_LOOP"))
                .toList()).isEmpty();
    }

    private FlowNode node(String id, NodeType type) {
        return FlowNode.builder()
                .id(id)
                .type(type)
                .build();
    }

    private FlowEdge edge(String id, String source, String target, String condition) {
        return FlowEdge.builder()
                .id(id)
                .sourceNodeId(source)
                .targetNodeId(target)
                .condition(condition)
                .build();
    }
}
