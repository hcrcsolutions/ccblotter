package com.example.agentmonitor.repository;

import com.example.agentmonitor.entity.NodeEntity;
import com.example.agentmonitor.model.InfraServerType;
import com.example.agentmonitor.model.ServerHealthStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NodeRepository extends JpaRepository<NodeEntity, String> {

    @Query("SELECT n FROM NodeEntity n JOIN FETCH n.datacenter")
    List<NodeEntity> findAllWithDatacenter();

    List<NodeEntity> findByType(InfraServerType type);

    List<NodeEntity> findByHealthStatus(ServerHealthStatus status);

    @Query("SELECT n FROM NodeEntity n JOIN FETCH n.datacenter WHERE n.healthStatus = :status")
    List<NodeEntity> findByHealthStatusWithDatacenter(@Param("status") ServerHealthStatus status);

    @Query("SELECT n FROM NodeEntity n WHERE n.datacenter.id = :datacenterId")
    List<NodeEntity> findByDatacenterId(@Param("datacenterId") String datacenterId);

    @Query("SELECT n FROM NodeEntity n WHERE n.type = :type AND n.datacenter.id = :datacenterId")
    List<NodeEntity> findByTypeAndDatacenterId(
            @Param("type") InfraServerType type,
            @Param("datacenterId") String datacenterId);

    List<NodeEntity> findByLastHeartbeatAtBeforeAndHealthStatus(
            Instant threshold,
            ServerHealthStatus status);

    List<NodeEntity> findByLastHeartbeatAtBefore(Instant threshold);

    @Query("SELECT n FROM NodeEntity n WHERE n.type = :type AND n.healthStatus = :status")
    List<NodeEntity> findByTypeAndHealthStatus(
            @Param("type") InfraServerType type,
            @Param("status") ServerHealthStatus status);

    @Query("SELECT COUNT(n) FROM NodeEntity n WHERE n.type = :type")
    long countByType(@Param("type") InfraServerType type);

    @Query("SELECT COUNT(n) FROM NodeEntity n WHERE n.healthStatus = :status")
    long countByHealthStatus(@Param("status") ServerHealthStatus status);

    @Query("SELECT COUNT(n) FROM NodeEntity n WHERE n.datacenter.id = :datacenterId")
    long countByDatacenterId(@Param("datacenterId") String datacenterId);

    @Query("SELECT COUNT(n) FROM NodeEntity n WHERE n.datacenter.id = :datacenterId AND n.healthStatus = :status")
    long countByDatacenterIdAndHealthStatus(
            @Param("datacenterId") String datacenterId,
            @Param("status") ServerHealthStatus status);

    @Modifying
    @Query("UPDATE NodeEntity n SET n.lastHeartbeatAt = :timestamp, n.updatedAt = :timestamp WHERE n.healthStatus = :status")
    int updateHeartbeatTimestampBatch(
            @Param("timestamp") Instant timestamp,
            @Param("status") ServerHealthStatus status);
}
