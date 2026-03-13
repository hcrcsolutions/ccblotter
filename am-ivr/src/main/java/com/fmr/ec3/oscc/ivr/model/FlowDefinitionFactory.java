package com.fmr.ec3.oscc.ivr.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.ivr.IvrFlowException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link FlowDefinition} from flow metadata and
 * raw content JSON ({@code nodes}, {@code edges}, {@code variables}).
 *
 * <p>Used by both am-api (on publish) and ivr-server
 * (on DB cache init / Kafka events) so the parsing
 * logic is never duplicated.
 */
public final class FlowDefinitionFactory {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private FlowDefinitionFactory() {
    }

    /**
     * Merges flow metadata with the content JSON stored in
     * {@code ivr_flow_content.content} and deserializes the
     * result as a {@link FlowDefinition}.
     *
     * @param objectMapper shared Jackson mapper
     * @param flowId       flow UUID as string
     * @param name         flow name
     * @param description  optional description (may be null)
     * @param entryNodeId  entry node id (may be null for drafts)
     * @param maxSessionDurationSeconds session timeout
     * @param maxSteps     max execution steps
     * @param version      published version number
     * @param status       flow status
     * @param contentJson  raw JSON containing nodes/edges/variables
     * @return fully-populated FlowDefinition
     * @throws IvrFlowException if the JSON is unparseable or
     *                          violates model constraints
     */
    @SuppressWarnings("unchecked")
    public static FlowDefinition fromContentJson(
            ObjectMapper objectMapper,
            String flowId,
            String name,
            String description,
            String entryNodeId,
            int maxSessionDurationSeconds,
            int maxSteps,
            int version,
            FlowStatus status,
            String contentJson) {
        try {
            Map<String, Object> content =
                    objectMapper.readValue(contentJson, MAP_TYPE);

            Map<String, Object> combined = new LinkedHashMap<>();
            combined.put("id", flowId);
            combined.put("name", name);
            combined.put("description", description);
            combined.put("entryNodeId", entryNodeId);
            combined.put("maxSessionDurationSeconds",
                    maxSessionDurationSeconds);
            combined.put("maxSteps", maxSteps);
            combined.put("version", version);
            combined.put("status", status.name());
            combined.put("nodes",
                    content.getOrDefault("nodes", List.of()));
            combined.put("edges",
                    content.getOrDefault("edges", List.of()));
            combined.put("variables",
                    content.getOrDefault("variables", List.of()));

            return objectMapper.convertValue(
                    combined, FlowDefinition.class);
        } catch (Exception e) {
            throw new IvrFlowException(
                    "Failed to parse flow definition for flowId="
                            + flowId, e);
        }
    }
}
