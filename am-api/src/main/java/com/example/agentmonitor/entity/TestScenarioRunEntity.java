package com.example.agentmonitor.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_scenario_runs", schema = "osccstate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestScenarioRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "scenario_id", nullable = false)
    private UUID scenarioId;

    @Column(name = "scenario_version", nullable = false)
    private int scenarioVersion;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "timing_mode", length = 20, nullable = false)
    @Builder.Default
    private String timingMode = "COMPRESSED";

    @Column(name = "result", length = 20)
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", columnDefinition = "jsonb")
    private String transcript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assertion_results", columnDefinition = "jsonb")
    private String assertionResults;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
