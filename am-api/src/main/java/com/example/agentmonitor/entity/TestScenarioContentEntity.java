package com.example.agentmonitor.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_scenario_content", schema = "osccstate")
@IdClass(TestScenarioContentId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestScenarioContentEntity {

    @Id
    @Column(name = "scenario_id")
    private UUID scenarioId;

    @Id
    @Column(name = "version")
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
