package com.fmr.ec3.oscc.state.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "datacenters", schema = "osccstate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatacenterEntity {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "region", length = 50, nullable = false)
    private String region;

    @Column(name = "display_name", length = 100)
    private String displayName;

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
