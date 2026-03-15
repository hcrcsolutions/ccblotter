package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.BusinessUnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessUnitRepository extends JpaRepository<BusinessUnitEntity, String> {

    List<BusinessUnitEntity> findAllByOrderByNameAsc();
}
