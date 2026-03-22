package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.TestScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestScenarioRepository extends JpaRepository<TestScenarioEntity, UUID> {

    List<TestScenarioEntity> findAllByOrderByUpdatedAtDesc();
}
