package com.fmr.ec3.oscc.state.repository;

import com.fmr.ec3.oscc.state.entity.DatacenterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatacenterRepository extends JpaRepository<DatacenterEntity, String> {

    List<DatacenterEntity> findByRegion(String region);
}
