package com.fmr.ec3.oscc.ivr.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.ivr.IvrFlowException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowDefinitionFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesFullFlowFromContentJson() {
        String contentJson = """
                {
                  "nodes": [
                    {
                      "id": "n1",
                      "type": "PLAY_PROMPT",
                      "label": "Greeting",
                      "position": {"x": 100.0, "y": 200.0},
                      "config": {"promptText": "Hello"}
                    }
                  ],
                  "edges": [
                    {
                      "id": "e1",
                      "sourceNodeId": "n1",
                      "targetNodeId": "n2",
                      "condition": "default"
                    }
                  ],
                  "variables": [
                    {
                      "name": "caller_id",
                      "type": "STRING",
                      "defaultValue": "unknown"
                    }
                  ]
                }
                """;

        FlowDefinition flow = FlowDefinitionFactory.fromContentJson(
                objectMapper,
                "f-123", "Test Flow", "A test", "n1",
                300, 50, 2, FlowStatus.PUBLISHED,
                contentJson);

        assertThat(flow.getId()).isEqualTo("f-123");
        assertThat(flow.getName()).isEqualTo("Test Flow");
        assertThat(flow.getDescription()).isEqualTo("A test");
        assertThat(flow.getEntryNodeId()).isEqualTo("n1");
        assertThat(flow.getMaxSessionDurationSeconds()).isEqualTo(300);
        assertThat(flow.getMaxSteps()).isEqualTo(50);
        assertThat(flow.getVersion()).isEqualTo(2);
        assertThat(flow.getStatus()).isEqualTo(FlowStatus.PUBLISHED);

        assertThat(flow.getNodes()).hasSize(1);
        FlowNode node = flow.getNodes().get(0);
        assertThat(node.getId()).isEqualTo("n1");
        assertThat(node.getType()).isEqualTo(NodeType.PLAY_PROMPT);
        assertThat(node.getLabel()).isEqualTo("Greeting");
        assertThat(node.getConfig()).containsEntry("promptText", "Hello");

        assertThat(flow.getEdges()).hasSize(1);
        FlowEdge edge = flow.getEdges().get(0);
        assertThat(edge.getSourceNodeId()).isEqualTo("n1");
        assertThat(edge.getTargetNodeId()).isEqualTo("n2");
        assertThat(edge.getCondition()).isEqualTo("default");

        assertThat(flow.getVariables()).hasSize(1);
        FlowVariable var = flow.getVariables().get(0);
        assertThat(var.getName()).isEqualTo("caller_id");
        assertThat(var.getType()).isEqualTo(VariableType.STRING);
        assertThat(var.getDefaultValue()).isEqualTo("unknown");
    }

    @Test
    void handlesNullDescription() {
        String contentJson = """
                {"nodes":[],"edges":[],"variables":[]}
                """;

        FlowDefinition flow = FlowDefinitionFactory.fromContentJson(
                objectMapper,
                "f-1", "Flow", null, "n1",
                600, 100, 1, FlowStatus.PUBLISHED,
                contentJson);

        assertThat(flow.getDescription()).isNull();
    }

    @Test
    void handlesEmptyContentArrays() {
        String contentJson = """
                {"nodes":[],"edges":[],"variables":[]}
                """;

        FlowDefinition flow = FlowDefinitionFactory.fromContentJson(
                objectMapper,
                "f-1", "Flow", null, "n1",
                600, 100, 1, FlowStatus.PUBLISHED,
                contentJson);

        assertThat(flow.getNodes()).isEmpty();
        assertThat(flow.getEdges()).isEmpty();
        assertThat(flow.getVariables()).isEmpty();
    }

    @Test
    void handlesMissingContentKeys() {
        String contentJson = "{}";

        FlowDefinition flow = FlowDefinitionFactory.fromContentJson(
                objectMapper,
                "f-1", "Flow", null, "n1",
                600, 100, 1, FlowStatus.PUBLISHED,
                contentJson);

        assertThat(flow.getNodes()).isEmpty();
        assertThat(flow.getEdges()).isEmpty();
        assertThat(flow.getVariables()).isEmpty();
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() ->
                FlowDefinitionFactory.fromContentJson(
                        objectMapper,
                        "f-1", "Flow", null, "n1",
                        600, 100, 1, FlowStatus.PUBLISHED,
                        "not-json"))
                .isInstanceOf(IvrFlowException.class)
                .hasMessageContaining("f-1");
    }

    @Test
    void throwsOnInvalidNodeType() {
        String contentJson = """
                {
                  "nodes": [{"id":"n1","type":"INVALID_TYPE"}],
                  "edges": [],
                  "variables": []
                }
                """;

        assertThatThrownBy(() ->
                FlowDefinitionFactory.fromContentJson(
                        objectMapper,
                        "f-1", "Flow", null, "n1",
                        600, 100, 1, FlowStatus.PUBLISHED,
                        contentJson))
                .isInstanceOf(IvrFlowException.class)
                .hasMessageContaining("f-1");
    }

    @Test
    void positionInContentJsonDoesNotLeakIntoSerialization()
            throws Exception {
        String contentJson = """
                {
                  "nodes": [
                    {
                      "id": "n1",
                      "type": "PLAY_PROMPT",
                      "position": {"x": 100, "y": 200}
                    }
                  ],
                  "edges": [],
                  "variables": []
                }
                """;

        FlowDefinition flow = FlowDefinitionFactory.fromContentJson(
                objectMapper,
                "f-1", "Flow", null, "n1",
                600, 100, 1, FlowStatus.PUBLISHED,
                contentJson);

        // Node parsed successfully despite position in content
        assertThat(flow.getNodes()).hasSize(1);

        // Serialized output (= what goes into Kafka) must
        // NOT contain "position"
        String json = objectMapper.writeValueAsString(flow);
        assertThat(json).doesNotContain("position");
    }

    @Test
    void producesIdenticalResultToJacksonRoundTrip() {
        String contentJson = """
                {
                  "nodes": [
                    {"id":"n1","type":"MENU","label":"Main Menu",
                     "config":{"timeout":5}}
                  ],
                  "edges": [
                    {"id":"e1","sourceNodeId":"n1",
                     "targetNodeId":"n2"}
                  ],
                  "variables": [
                    {"name":"lang","type":"STRING",
                     "defaultValue":"en"}
                  ]
                }
                """;

        FlowDefinition flow = FlowDefinitionFactory.fromContentJson(
                objectMapper,
                "f-99", "Round Trip", "desc", "n1",
                120, 20, 5, FlowStatus.PUBLISHED,
                contentJson);

        // Serialize back and deserialize again
        // — must produce an equivalent object
        try {
            String json = objectMapper.writeValueAsString(flow);
            FlowDefinition roundTripped =
                    objectMapper.readValue(json, FlowDefinition.class);

            assertThat(roundTripped.getId())
                    .isEqualTo(flow.getId());
            assertThat(roundTripped.getName())
                    .isEqualTo(flow.getName());
            assertThat(roundTripped.getVersion())
                    .isEqualTo(flow.getVersion());
            assertThat(roundTripped.getStatus())
                    .isEqualTo(flow.getStatus());
            assertThat(roundTripped.getNodes())
                    .hasSize(flow.getNodes().size());
            assertThat(roundTripped.getEdges())
                    .hasSize(flow.getEdges().size());
            assertThat(roundTripped.getVariables())
                    .hasSize(flow.getVariables().size());
        } catch (Exception e) {
            throw new AssertionError("Round-trip failed", e);
        }
    }
}
