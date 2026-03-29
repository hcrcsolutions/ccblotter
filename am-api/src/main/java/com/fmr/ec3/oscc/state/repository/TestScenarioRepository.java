package com.fmr.ec3.oscc.state.repository;

import com.fmr.ec3.oscc.state.entity.TestScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestScenarioRepository extends JpaRepository<TestScenarioEntity, UUID> {

    List<TestScenarioEntity> findAllByOrderByUpdatedAtDesc();
}
