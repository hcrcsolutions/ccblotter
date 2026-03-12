package com.example.agentmonitor.service;

import com.example.agentmonitor.dto.request.CreateFlowRequest;
import com.example.agentmonitor.dto.request.SaveFlowContentRequest;
import com.example.agentmonitor.dto.request.UpdateFlowRequest;
import com.example.agentmonitor.dto.response.*;
import com.example.agentmonitor.entity.IvrFlowContentEntity;
import com.example.agentmonitor.entity.IvrFlowEntity;
import com.example.agentmonitor.exception.IvrFlowConflictException;
import com.example.agentmonitor.exception.IvrFlowNotFoundException;
import com.example.agentmonitor.repository.IvrFlowContentRepository;
import com.example.agentmonitor.repository.IvrFlowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.common.EventType;
import com.fmr.ec3.oscc.common.KafkaTopics;
import com.fmr.ec3.oscc.common.payload.ivr.IvrFlowPublishedPayload;
import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.model.FlowEdge;
import com.fmr.ec3.oscc.ivr.model.FlowNode;
import com.fmr.ec3.oscc.ivr.model.FlowVariable;
import com.fmr.ec3.oscc.ivr.model.NodeType;
import com.fmr.ec3.oscc.ivr.model.VariableType;
import com.fmr.ec3.oscc.ivr.validation.FlowValidator;
import com.fmr.ec3.oscc.ivr.validation.ValidationIssue;
import com.fmr.ec3.oscc.ivr.validation.ValidationResult;
import com.fmr.ec3.oscc.sender.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class IvrFlowService {

    private final IvrFlowRepository flowRepository;
    private final IvrFlowContentRepository contentRepository;
    private final ObjectMapper objectMapper;
    private final EventProducer eventProducer;
    private final FlowValidator flowValidator = new FlowValidator();

    @Transactional(readOnly = true)
    public List<FlowSummaryDto> listFlows() {
        return flowRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowDetailDto getFlow(UUID id) {
        IvrFlowEntity entity = findFlowOrThrow(id);
        return toDetail(entity);
    }

    @Transactional
    public FlowDetailDto createFlow(CreateFlowRequest request) {
        IvrFlowEntity entity = IvrFlowEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        entity = flowRepository.save(entity);
        log.info("Created IVR flow: {} ({})", entity.getName(), entity.getId());
        return toDetail(entity);
    }

    @Transactional
    public FlowDetailDto updateFlow(UUID id, UpdateFlowRequest request) {
        IvrFlowEntity entity = findFlowOrThrow(id);

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());

        if (request.getEntryNodeId() != null) {
            entity.setEntryNodeId(request.getEntryNodeId());
        }
        if (request.getMaxSessionDurationSeconds() != null) {
            entity.setMaxSessionDurationSeconds(request.getMaxSessionDurationSeconds());
        }
        if (request.getMaxSteps() != null) {
            entity.setMaxSteps(request.getMaxSteps());
        }

        entity = flowRepository.save(entity);
        log.info("Updated IVR flow: {} ({})", entity.getName(), entity.getId());
        return toDetail(entity);
    }

    @Transactional
    public void deleteFlow(UUID id) {
        IvrFlowEntity entity = findFlowOrThrow(id);
        flowRepository.delete(entity);
        log.info("Deleted IVR flow: {} ({})", entity.getName(), id);
    }

    @Transactional(readOnly = true)
    public FlowContentDto getContent(UUID flowId) {
        findFlowOrThrow(flowId);
        return contentRepository.findFirstByFlowIdOrderByVersionDesc(flowId)
                .map(this::toContentDto)
                .orElse(FlowContentDto.builder()
                        .flowId(flowId)
                        .version(0)
                        .content("{\"nodes\":[],\"edges\":[],\"variables\":[]}")
                        .build());
    }

    @Transactional
    public FlowContentDto saveContent(UUID flowId, SaveFlowContentRequest request) {
        IvrFlowEntity flow = findFlowOrThrow(flowId);

        int nextVersion = contentRepository.findFirstByFlowIdOrderByVersionDesc(flowId)
                .map(c -> c.getVersion() + 1)
                .orElse(1);

        String contentJson = serializeContent(request);

        IvrFlowContentEntity contentEntity = IvrFlowContentEntity.builder()
                .flowId(flowId)
                .version(nextVersion)
                .content(contentJson)
                .createdBy(request.getCreatedBy())
                .build();

        contentEntity = contentRepository.save(contentEntity);

        flow.setVersion(nextVersion);
        flowRepository.save(flow);

        log.info("Saved IVR flow content: {} version {}", flowId, nextVersion);
        return toContentDto(contentEntity);
    }

    @Transactional
    public PublishResultDto publishFlow(UUID flowId) {
        IvrFlowEntity flow = findFlowOrThrow(flowId);

        if ("PUBLISHED".equals(flow.getStatus())) {
            throw new IvrFlowConflictException(flowId, "Flow is already published");
        }

        FlowContentDto content = getContent(flowId);
        FlowDefinition definition = parseFlowDefinition(flow, content);

        ValidationResult validation = flowValidator.validate(definition);

        List<Map<String, Object>> issues = validation.getIssues().stream()
                .map(this::toIssueMap)
                .toList();

        if (!validation.isValid()) {
            return PublishResultDto.builder()
                    .flowId(flowId)
                    .published(false)
                    .version(flow.getVersion())
                    .validationIssues(issues)
                    .message("Flow validation failed with " + validation.getErrors().size() + " error(s)")
                    .build();
        }

        flow.setStatus("PUBLISHED");
        flowRepository.save(flow);

        sendFlowPublishedEvent(flowId, definition, flow.getVersion());
        log.info("Published IVR flow: {} version {}", flowId, flow.getVersion());

        return PublishResultDto.builder()
                .flowId(flowId)
                .published(true)
                .version(flow.getVersion())
                .validationIssues(issues)
                .message("Flow published successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public FlowDetailDto getPublishedFlow(UUID flowId) {
        return flowRepository.findByIdAndStatus(flowId, "PUBLISHED")
                .map(this::toDetail)
                .orElseThrow(() -> new IvrFlowNotFoundException(flowId));
    }

    @Transactional(readOnly = true)
    public List<FlowVersionDto> getVersions(UUID flowId) {
        findFlowOrThrow(flowId);
        return contentRepository.findByFlowIdOrderByVersionDesc(flowId).stream()
                .map(c -> FlowVersionDto.builder()
                        .version(c.getVersion())
                        .createdAt(c.getCreatedAt())
                        .createdBy(c.getCreatedBy())
                        .build())
                .toList();
    }

    private void sendFlowPublishedEvent(UUID flowId, FlowDefinition definition, int version) {
        try {
            String flowDefJson = objectMapper.writeValueAsString(definition);
            IvrFlowPublishedPayload payload = new IvrFlowPublishedPayload(
                    flowId.toString(), flowDefJson, version, System.currentTimeMillis());
            eventProducer.send(
                    KafkaTopics.IVR_FLOWS,
                    flowId.toString(),
                    EventType.IVR_FLOW_PUBLISHED,
                    "am-api",
                    flowId.toString(),
                    payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize flow definition for Kafka event: {}", flowId, e);
        }
    }

    private IvrFlowEntity findFlowOrThrow(UUID id) {
        return flowRepository.findById(id)
                .orElseThrow(() -> new IvrFlowNotFoundException(id));
    }

    private FlowSummaryDto toSummary(IvrFlowEntity entity) {
        return FlowSummaryDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FlowDetailDto toDetail(IvrFlowEntity entity) {
        return FlowDetailDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .entryNodeId(entity.getEntryNodeId())
                .maxSessionDurationSeconds(entity.getMaxSessionDurationSeconds())
                .maxSteps(entity.getMaxSteps())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FlowContentDto toContentDto(IvrFlowContentEntity entity) {
        return FlowContentDto.builder()
                .flowId(entity.getFlowId())
                .version(entity.getVersion())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }

    private String serializeContent(SaveFlowContentRequest request) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("nodes", request.getNodes());
            content.put("edges", request.getEdges());
            content.put("variables", request.getVariables() != null ? request.getVariables() : List.of());
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize flow content", e);
        }
    }

    @SuppressWarnings("unchecked")
    private FlowDefinition parseFlowDefinition(IvrFlowEntity flow, FlowContentDto content) {
        try {
            Map<String, Object> contentMap = objectMapper.readValue(content.getContent(), Map.class);

            List<Map<String, Object>> nodesRaw = (List<Map<String, Object>>) contentMap.getOrDefault("nodes", List.of());
            List<Map<String, Object>> edgesRaw = (List<Map<String, Object>>) contentMap.getOrDefault("edges", List.of());
            List<Map<String, Object>> varsRaw = (List<Map<String, Object>>) contentMap.getOrDefault("variables", List.of());

            List<FlowNode> nodes = nodesRaw.stream()
                    .map(this::parseNode)
                    .toList();

            List<FlowEdge> edges = edgesRaw.stream()
                    .map(this::parseEdge)
                    .toList();

            List<FlowVariable> variables = varsRaw.stream()
                    .map(this::parseVariable)
                    .toList();

            return FlowDefinition.builder()
                    .id(flow.getId().toString())
                    .name(flow.getName())
                    .entryNodeId(flow.getEntryNodeId())
                    .nodes(nodes)
                    .edges(edges)
                    .variables(variables)
                    .maxSessionDurationSeconds(flow.getMaxSessionDurationSeconds())
                    .maxSteps(flow.getMaxSteps())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse flow content", e);
        }
    }

    @SuppressWarnings("unchecked")
    private FlowNode parseNode(Map<String, Object> raw) {
        Map<String, Object> config = (Map<String, Object>) raw.getOrDefault("config", Map.of());
        return FlowNode.builder()
                .id((String) raw.get("id"))
                .type(NodeType.valueOf((String) raw.get("type")))
                .label((String) raw.get("label"))
                .config(config)
                .build();
    }

    private FlowEdge parseEdge(Map<String, Object> raw) {
        return FlowEdge.builder()
                .id((String) raw.get("id"))
                .sourceNodeId((String) raw.get("sourceNodeId"))
                .targetNodeId((String) raw.get("targetNodeId"))
                .condition((String) raw.get("condition"))
                .build();
    }

    private FlowVariable parseVariable(Map<String, Object> raw) {
        FlowVariable.Builder builder = FlowVariable.builder()
                .name((String) raw.get("name"));

        String type = (String) raw.get("type");
        if (type != null) {
            builder.type(VariableType.valueOf(type));
        } else {
            builder.type(VariableType.STRING);
        }

        builder.defaultValue((String) raw.get("defaultValue"));
        return builder.build();
    }

    private Map<String, Object> toIssueMap(ValidationIssue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", issue.getSeverity().name());
        map.put("nodeId", issue.getNodeId());
        map.put("code", issue.getCode());
        map.put("message", issue.getMessage());
        return map;
    }
}
