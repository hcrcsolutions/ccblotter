package com.example.agentmonitor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ivr_flows", schema = "osccstate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrFlowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 0;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "entry_node_id", length = 50)
    private String entryNodeId;

    @Column(name = "max_session_duration_seconds", nullable = false)
    @Builder.Default
    private int maxSessionDurationSeconds = 600;

    @Column(name = "max_steps", nullable = false)
    @Builder.Default
    private int maxSteps = 100;

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
