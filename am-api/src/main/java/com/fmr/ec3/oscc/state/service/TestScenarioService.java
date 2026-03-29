package com.fmr.ec3.oscc.state.service;

import com.fmr.ec3.oscc.state.dto.request.CreateScenarioRequest;
import com.fmr.ec3.oscc.state.dto.request.SaveScenarioContentRequest;
import com.fmr.ec3.oscc.state.dto.request.UpdateScenarioRequest;
import com.fmr.ec3.oscc.state.dto.response.*;
import com.fmr.ec3.oscc.state.entity.TestScenarioContentEntity;
import com.fmr.ec3.oscc.state.entity.TestScenarioEntity;
import com.fmr.ec3.oscc.state.entity.TestScenarioRunEntity;
import com.fmr.ec3.oscc.state.exception.TestScenarioNotFoundException;
import com.fmr.ec3.oscc.state.repository.TestScenarioContentRepository;
import com.fmr.ec3.oscc.state.repository.TestScenarioRepository;
import com.fmr.ec3.oscc.state.repository.TestScenarioRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestScenarioService {

    private final TestScenarioRepository scenarioRepository;
    private final TestScenarioContentRepository contentRepository;
    private final TestScenarioRunRepository runRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ScenarioSummaryDto> listScenarios() {
        List<TestScenarioEntity> scenarios = scenarioRepository.findAllByOrderByUpdatedAtDesc();
        return scenarios.stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScenarioDetailDto getScenario(UUID id) {
        TestScenarioEntity entity = findScenarioOrThrow(id);
        return toDetail(entity);
    }

    @Transactional
    public ScenarioDetailDto createScenario(CreateScenarioRequest request) {
        TestScenarioEntity entity = TestScenarioEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();

        entity = scenarioRepository.save(entity);
        log.info("Created test scenario: {} ({})", entity.getName(), entity.getId());
        return toDetail(entity);
    }

    @Transactional
    public ScenarioDetailDto updateScenario(UUID id, UpdateScenarioRequest request) {
        TestScenarioEntity entity = findScenarioOrThrow(id);

        entity.setName(request.getName());
        entity.setDescription(request.getDescription());

        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }

        entity = scenarioRepository.save(entity);
        log.info("Updated test scenario: {} ({})", entity.getName(), entity.getId());
        return toDetail(entity);
    }

    @Transactional
    public void deleteScenario(UUID id) {
        TestScenarioEntity entity = findScenarioOrThrow(id);
        scenarioRepository.delete(entity);
        log.info("Deleted test scenario: {} ({})", entity.getName(), id);
    }

    @Transactional(readOnly = true)
    public ScenarioContentDto getContent(UUID scenarioId) {
        findScenarioOrThrow(scenarioId);
        return contentRepository.findFirstByScenarioIdOrderByVersionDesc(scenarioId)
                .map(this::toContentDto)
                .orElse(ScenarioContentDto.builder()
                        .scenarioId(scenarioId)
                        .version(0)
                        .content("{\"actors\":[],\"steps\":[],\"assertions\":[],\"settings\":{\"defaultTimingMode\":\"COMPRESSED\",\"compressedTimeScale\":10,\"cleanupAfterRun\":true}}")
                        .build());
    }

    @Transactional
    public ScenarioContentDto saveContent(UUID scenarioId, SaveScenarioContentRequest request) {
        TestScenarioEntity scenario = findScenarioOrThrow(scenarioId);

        int nextVersion = contentRepository.findFirstByScenarioIdOrderByVersionDesc(scenarioId)
                .map(c -> c.getVersion() + 1)
                .orElse(1);

        String contentJson = serializeContent(request.getContent());

        TestScenarioContentEntity contentEntity = TestScenarioContentEntity.builder()
                .scenarioId(scenarioId)
                .version(nextVersion)
                .content(contentJson)
                .createdBy(request.getCreatedBy())
                .build();

        contentEntity = contentRepository.save(contentEntity);

        scenario.setVersion(nextVersion);
        scenarioRepository.save(scenario);

        log.info("Saved test scenario content: {} version {}", scenarioId, nextVersion);
        return toContentDto(contentEntity);
    }

    @Transactional(readOnly = true)
    public List<ScenarioVersionDto> getVersions(UUID scenarioId) {
        findScenarioOrThrow(scenarioId);
        return contentRepository.findByScenarioIdOrderByVersionDesc(scenarioId).stream()
                .map(c -> ScenarioVersionDto.builder()
                        .version(c.getVersion())
                        .createdAt(c.getCreatedAt())
                        .createdBy(c.getCreatedBy())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScenarioRunDto> listRuns(UUID scenarioId) {
        findScenarioOrThrow(scenarioId);
        return runRepository.findByScenarioIdOrderByCreatedAtDesc(scenarioId).stream()
                .map(this::toRunDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ScenarioRunDto getRun(UUID runId) {
        TestScenarioRunEntity entity = runRepository.findById(runId)
                .orElseThrow(() -> new TestScenarioNotFoundException(runId));
        return toRunDto(entity);
    }

    private TestScenarioEntity findScenarioOrThrow(UUID id) {
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new TestScenarioNotFoundException(id));
    }

    private ScenarioSummaryDto toSummary(TestScenarioEntity entity) {
        ScenarioSummaryDto.ScenarioSummaryDtoBuilder builder = ScenarioSummaryDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        runRepository.findFirstByScenarioIdOrderByCreatedAtDesc(entity.getId())
                .ifPresent(run -> {
                    builder.lastRunResult(run.getResult());
                    builder.lastRunAt(run.getCreatedAt());
                });

        return builder.build();
    }

    private ScenarioDetailDto toDetail(TestScenarioEntity entity) {
        return ScenarioDetailDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ScenarioContentDto toContentDto(TestScenarioContentEntity entity) {
        return ScenarioContentDto.builder()
                .scenarioId(entity.getScenarioId())
                .version(entity.getVersion())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }

    private ScenarioRunDto toRunDto(TestScenarioRunEntity entity) {
        return ScenarioRunDto.builder()
                .id(entity.getId())
                .scenarioId(entity.getScenarioId())
                .scenarioVersion(entity.getScenarioVersion())
                .status(entity.getStatus())
                .timingMode(entity.getTimingMode())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .result(entity.getResult())
                .transcript(entity.getTranscript())
                .assertionResults(entity.getAssertionResults())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String serializeContent(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize scenario content", e);
        }
    }
}
