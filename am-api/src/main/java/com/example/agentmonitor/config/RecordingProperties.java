package com.example.agentmonitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.recordings")
public class RecordingProperties {

    /**
     * Directory where WAV recordings are stored.
     */
    private String storageDir = "./recordings";

    /**
     * Maximum allowed file size in bytes (default 50 MB).
     */
    private long maxFileSizeBytes = 52428800L;
}
