package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.IvrFlowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IvrFlowRepository extends JpaRepository<IvrFlowEntity, UUID> {

    List<IvrFlowEntity> findAllByOrderByUpdatedAtDesc();

    Optional<IvrFlowEntity> findByIdAndStatus(UUID id, String status);
}
