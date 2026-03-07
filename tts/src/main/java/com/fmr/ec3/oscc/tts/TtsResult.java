package com.fmr.ec3.oscc.tts;

/**
 * Immutable result of a text-to-speech synthesis.
 */
public class TtsResult {

    private final byte[] pcmAudio;
    private final long durationMs;
    private final boolean fromCache;

    public TtsResult(byte[] pcmAudio, long durationMs, boolean fromCache) {
        this.pcmAudio = pcmAudio == null ? null : pcmAudio.clone();
        this.durationMs = durationMs;
        this.fromCache = fromCache;
    }

    public byte[] getPcmAudio() {
        return pcmAudio == null ? null : pcmAudio.clone();
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isFromCache() {
        return fromCache;
    }
}
