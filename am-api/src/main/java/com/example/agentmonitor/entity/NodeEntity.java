package com.example.agentmonitor.entity;

import com.example.agentmonitor.model.InfraServerType;
import com.example.agentmonitor.model.ServerHealthStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "nodes", schema = "osccstate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeEntity {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 10, nullable = false)
    private InfraServerType type;

    @Column(name = "hostname", length = 255, nullable = false)
    private String hostname;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datacenter_id", nullable = false)
    private DatacenterEntity datacenter;

    @Column(name = "max_sessions", nullable = false)
    private int maxSessions;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    @Column(name = "trunk_group", length = 50)
    private String trunkGroup;

    @Column(name = "maintenance_mode", nullable = false)
    @Builder.Default
    private boolean maintenanceMode = false;

    @Column(name = "registration_source", length = 20, nullable = false)
    @Builder.Default
    private String registrationSource = "SELF";

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", length = 20, nullable = false)
    @Builder.Default
    private ServerHealthStatus healthStatus = ServerHealthStatus.UNKNOWN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
