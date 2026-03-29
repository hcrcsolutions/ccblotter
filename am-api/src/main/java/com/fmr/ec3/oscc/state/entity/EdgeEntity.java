package com.fmr.ec3.oscc.state.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "edges", schema = "osccstate", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_id", "target_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EdgeEntity {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private NodeEntity source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private NodeEntity target;

    @Column(name = "bandwidth_mbps")
    private Integer bandwidthMbps;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Generate edge ID from source and target.
     */
    public static String generateId(String sourceId, String targetId) {
        return "e-" + sourceId + "-" + targetId;
    }
}
