package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.EdgeEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EdgeRepository extends JpaRepository<EdgeEntity, String> {

    @Query("SELECT e FROM EdgeEntity e JOIN FETCH e.source JOIN FETCH e.target")
    List<EdgeEntity> findAllWithNodes();

    @Query("SELECT e FROM EdgeEntity e JOIN FETCH e.source JOIN FETCH e.target WHERE e.id = :id")
    Optional<EdgeEntity> findByIdWithNodes(@Param("id") String id);

    @Query("SELECT e FROM EdgeEntity e JOIN FETCH e.source JOIN FETCH e.target WHERE e.source.id = :sourceId")
    List<EdgeEntity> findBySourceId(@Param("sourceId") String sourceId);

    @Query("SELECT e FROM EdgeEntity e JOIN FETCH e.source JOIN FETCH e.target WHERE e.target.id = :targetId")
    List<EdgeEntity> findByTargetId(@Param("targetId") String targetId);

    @Query("SELECT e FROM EdgeEntity e JOIN FETCH e.source JOIN FETCH e.target WHERE e.source.id = :sourceId AND e.target.id = :targetId")
    Optional<EdgeEntity> findBySourceIdAndTargetId(
            @Param("sourceId") String sourceId,
            @Param("targetId") String targetId);

    @Modifying
    @Query("DELETE FROM EdgeEntity e WHERE e.source.id = :sourceId")
    void deleteBySourceId(@Param("sourceId") String sourceId);

    @Modifying
    @Query("DELETE FROM EdgeEntity e WHERE e.source.id = :sourceId AND e.target.id = :targetId")
    void deleteBySourceIdAndTargetId(
            @Param("sourceId") String sourceId,
            @Param("targetId") String targetId);

    @Modifying
    @Query("DELETE FROM EdgeEntity e WHERE e.source.id = :sourceId AND e.target.id IN :targetIds")
    void deleteBySourceIdAndTargetIdIn(
            @Param("sourceId") String sourceId,
            @Param("targetIds") List<String> targetIds);

    @Query("SELECT COUNT(e) FROM EdgeEntity e WHERE e.source.id = :sourceId")
    long countBySourceId(@Param("sourceId") String sourceId);
}
