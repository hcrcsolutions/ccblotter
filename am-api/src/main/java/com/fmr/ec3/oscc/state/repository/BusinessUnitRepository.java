package com.fmr.ec3.oscc.state.repository;

import com.fmr.ec3.oscc.state.entity.BusinessUnitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessUnitRepository extends JpaRepository<BusinessUnitEntity, String> {

    List<BusinessUnitEntity> findAllByOrderByNameAsc();
}
