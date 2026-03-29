package com.fmr.ec3.oscc.state.repository;

import com.fmr.ec3.oscc.state.entity.RecordingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordingRepository extends JpaRepository<RecordingEntity, String> {

    List<RecordingEntity> findAllByOrderByUploadedAtDesc();
}
