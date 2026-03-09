package com.fmr.ec3.oscc.audiostore;

import java.time.Instant;

/**
 * Immutable metadata for a stored audio.
 */
public class AudioMeta {

    private final String audioId;
    private final String sessionId;
    private final long size;
    private final Instant lastModified;

    public AudioMeta(String audioId, String sessionId, long size, Instant lastModified) {
        this.audioId = audioId;
        this.sessionId = sessionId;
        this.size = size;
        this.lastModified = lastModified;
    }

    public String getAudioId() {
        return audioId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getSize() {
        return size;
    }

    public Instant getLastModified() {
        return lastModified;
    }
}
