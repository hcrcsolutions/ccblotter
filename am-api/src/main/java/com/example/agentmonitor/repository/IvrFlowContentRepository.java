package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.IvrFlowContentEntity;
import com.example.agentmonitor.entity.IvrFlowContentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IvrFlowContentRepository extends JpaRepository<IvrFlowContentEntity, IvrFlowContentId> {

    List<IvrFlowContentEntity> findByFlowIdOrderByVersionDesc(UUID flowId);

    Optional<IvrFlowContentEntity> findByFlowIdAndVersion(UUID flowId, int version);

    Optional<IvrFlowContentEntity> findFirstByFlowIdOrderByVersionDesc(UUID flowId);
}
