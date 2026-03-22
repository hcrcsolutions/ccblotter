package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.TestScenarioContentEntity;
import com.example.agentmonitor.entity.TestScenarioContentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestScenarioContentRepository
        extends JpaRepository<TestScenarioContentEntity, TestScenarioContentId> {

    List<TestScenarioContentEntity> findByScenarioIdOrderByVersionDesc(UUID scenarioId);

    Optional<TestScenarioContentEntity> findFirstByScenarioIdOrderByVersionDesc(UUID scenarioId);
}
