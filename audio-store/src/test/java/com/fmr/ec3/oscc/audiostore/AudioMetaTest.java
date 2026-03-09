package com.fmr.ec3.oscc.audiostore;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AudioMetaTest {

    @Test
    void storesAllFields() {
        Instant now = Instant.now();
        AudioMeta meta = new AudioMeta("utt-1", "session-1", 1024, now);

        assertThat(meta.getAudioId()).isEqualTo("utt-1");
        assertThat(meta.getSessionId()).isEqualTo("session-1");
        assertThat(meta.getSize()).isEqualTo(1024);
        assertThat(meta.getLastModified()).isEqualTo(now);
    }

    @Test
    void storesZeroSize() {
        AudioMeta meta = new AudioMeta("id", "sess", 0, Instant.EPOCH);

        assertThat(meta.getSize()).isEqualTo(0);
    }

    @Test
    void storesLargeSize() {
        AudioMeta meta = new AudioMeta("id", "sess", Long.MAX_VALUE, Instant.EPOCH);

        assertThat(meta.getSize()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void storesNullLastModified() {
        AudioMeta meta = new AudioMeta("id", "sess", 100, null);

        assertThat(meta.getLastModified()).isNull();
    }

    @Test
    void storesEpochLastModified() {
        AudioMeta meta = new AudioMeta("id", "sess", 100, Instant.EPOCH);

        assertThat(meta.getLastModified()).isEqualTo(Instant.EPOCH);
    }
}
