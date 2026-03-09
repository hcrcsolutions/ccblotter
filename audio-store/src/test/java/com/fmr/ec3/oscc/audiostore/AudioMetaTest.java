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
}
