package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.DatacenterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatacenterRepository extends JpaRepository<DatacenterEntity, String> {

    List<DatacenterEntity> findByRegion(String region);
}
