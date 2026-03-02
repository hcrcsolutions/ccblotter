package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.RecordingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordingRepository extends JpaRepository<RecordingEntity, String> {

    List<RecordingEntity> findAllByOrderByUploadedAtDesc();
}
