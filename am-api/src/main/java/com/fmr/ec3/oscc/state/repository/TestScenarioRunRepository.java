package com.fmr.ec3.oscc.state.repository;

import com.fmr.ec3.oscc.state.entity.TestScenarioRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestScenarioRunRepository extends JpaRepository<TestScenarioRunEntity, UUID> {

    List<TestScenarioRunEntity> findByScenarioIdOrderByCreatedAtDesc(UUID scenarioId);

    Optional<TestScenarioRunEntity> findFirstByScenarioIdOrderByCreatedAtDesc(UUID scenarioId);
}
