package com.example.agentmonitor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "business_units", schema = "osccstate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessUnitEntity {

    @Id
    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
