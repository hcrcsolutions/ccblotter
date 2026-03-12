package com.fmr.ec3.oscc.ivr.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapturedInputTest {

    @Test
    void buildWithAllFields() {
        CapturedInput input = CapturedInput.builder()
                .audioData("base64audio==")
                .audioFormat("pcm")
                .sampleRate(8000)
                .build();

        assertThat(input.getAudioData()).isEqualTo("base64audio==");
        assertThat(input.getAudioFormat()).isEqualTo("pcm");
        assertThat(input.getSampleRate()).isEqualTo(8000);
    }

    @Test
    void nullAudioDataThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> CapturedInput.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioData is required");
    }

    @Test
    void defaultAudioFormatIsWav() {
        CapturedInput input = CapturedInput.builder()
                .audioData("data")
                .build();

        assertThat(input.getAudioFormat()).isEqualTo("wav");
    }

    @Test
    void defaultSampleRateIs16000() {
        CapturedInput input = CapturedInput.builder()
                .audioData("data")
                .build();

        assertThat(input.getSampleRate()).isEqualTo(16000);
    }
}
