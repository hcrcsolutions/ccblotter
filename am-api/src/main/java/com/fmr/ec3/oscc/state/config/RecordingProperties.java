package com.fmr.ec3.oscc.state.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.recordings")
public class RecordingProperties {

    /**
     * Directory where WAV recordings are stored (local mode).
     */
    private String storageDir = "./recordings";

    /**
     * Maximum allowed file size in bytes (default 50 MB).
     */
    private long maxFileSizeBytes = 52428800L;

    /**
     * Storage backend: "local" or "webdav".
     */
    private String storage = "local";

    /**
     * Public base URL for serving recordings (WebDAV mode).
     */
    private String baseUrl = "";

    /**
     * WebDAV server URL for file operations.
     */
    private String webdavUrl = "";

    /**
     * WebDAV Basic auth username.
     */
    private String webdavUsername = "";

    /**
     * WebDAV Basic auth password.
     */
    private String webdavPassword = "";

    /**
     * Skip SSL certificate verification for WebDAV (dev only).
     */
    private boolean webdavInsecureSsl = false;
}
