package com.example.agentmonitor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "recordings", schema = "osccstate")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingEntity {

    @Id
    @Column(name = "filename", length = 255)
    private String filename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    @Column(name = "channels")
    private Integer channels;

    @Column(name = "bits_per_sample")
    private Integer bitsPerSample;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = Instant.now();
        }
    }
}
