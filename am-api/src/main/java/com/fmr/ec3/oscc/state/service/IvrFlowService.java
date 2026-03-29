package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.dto.request.CreateFlowRequest;
import com.fmr.ec3.oscc.state.dto.request.SaveFlowContentRequest;
import com.fmr.ec3.oscc.state.dto.request.UpdateFlowRequest;
import com.fmr.ec3.oscc.state.dto.response.*;
import com.fmr.ec3.oscc.state.entity.IvrFlowContentEntity;
import com.fmr.ec3.oscc.state.entity.IvrFlowEntity;
import com.fmr.ec3.oscc.state.event.FlowPublishedEvent;
import com.fmr.ec3.oscc.state.event.FlowUnpublishedEvent;
import com.fmr.ec3.oscc.state.exception.IvrFlowConflictException;
import com.fmr.ec3.oscc.state.exception.IvrFlowNotFoundException;
import com.fmr.ec3.oscc.state.repository.IvrFlowContentRepository;
import com.fmr.ec3.oscc.state.repository.IvrFlowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmr.ec3.oscc.ivr.model.FlowDefinition;
import com.fmr.ec3.oscc.ivr.model.FlowDefinitionFactory;
import com.fmr.ec3.oscc.ivr.model.FlowStatus;
import com.fmr.ec3.oscc.ivr.validation.FlowValidator;
import com.fmr.ec3.oscc.ivr.validation.ValidationIssue;
import com.fmr.ec3.oscc.ivr.validation.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class IvrFlowService {

    private final IvrFlowRepository flowRepository;
    private final IvrFlowContentRepository contentRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
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
                .businessUnit(request.getBusinessUnit())
                .build();

        entity = flowRepository.save(entity);
        log.info("Created IVR flow: {} ({})", entity.getName(), entity.getId());
        return toDetail(entity);
    }

    @Transactional
    public FlowDetailDto updateFlow(UUID id, UpdateFlowRequest request) {
        IvrFlowEntity entity = findFlowOrThrow(id);

        if ("PUBLISHED".equals(entity.getStatus())) {
            throw new IvrFlowConflictException(id, "Cannot modify a published flow");
        }

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setBusinessUnit(request.getBusinessUnit());

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
        boolean wasPublished = "PUBLISHED".equals(entity.getStatus());
        flowRepository.delete(entity);
        if (wasPublished) {
            applicationEventPublisher.publishEvent(new FlowUnpublishedEvent(id));
        }
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

        if ("PUBLISHED".equals(flow.getStatus())) {
            throw new IvrFlowConflictException(flowId, "Cannot modify a published flow");
        }

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

        applicationEventPublisher.publishEvent(
                new FlowPublishedEvent(flowId, definition, flow.getVersion()));
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

    private IvrFlowEntity findFlowOrThrow(UUID id) {
        return flowRepository.findById(id)
                .orElseThrow(() -> new IvrFlowNotFoundException(id));
    }

    private FlowSummaryDto toSummary(IvrFlowEntity entity) {
        return FlowSummaryDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .businessUnit(entity.getBusinessUnit())
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
                .businessUnit(entity.getBusinessUnit())
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

    private FlowDefinition parseFlowDefinition(
            IvrFlowEntity flow, FlowContentDto content) {
        return FlowDefinitionFactory.fromContentJson(
                objectMapper,
                flow.getId().toString(),
                flow.getName(),
                flow.getDescription(),
                flow.getEntryNodeId(),
                flow.getMaxSessionDurationSeconds(),
                flow.getMaxSteps(),
                flow.getVersion(),
                FlowStatus.valueOf(flow.getStatus()),
                content.getContent());
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
