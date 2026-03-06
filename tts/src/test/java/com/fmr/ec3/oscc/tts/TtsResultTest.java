package com.fmr.ec3.oscc.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TtsResultTest {

    @Test
    void constructorAndGetters() {
        byte[] audio = {1, 2, 3, 4};
        TtsResult result = new TtsResult(audio, 500L, false);

        assertThat(result.getPcmAudio()).isEqualTo(audio);
        assertThat(result.getDurationMs()).isEqualTo(500L);
        assertThat(result.isFromCache()).isFalse();
    }

    @Test
    void fromCacheFlag() {
        TtsResult result = new TtsResult(new byte[0], 0L, true);

        assertThat(result.isFromCache()).isTrue();
    }
}
