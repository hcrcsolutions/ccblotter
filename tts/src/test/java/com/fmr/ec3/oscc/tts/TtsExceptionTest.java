package com.fmr.ec3.oscc.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TtsExceptionTest {

    @Test
    void messageConstructor() {
        TtsException ex = new TtsException("failed");

        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("root");
        TtsException ex = new TtsException("failed", cause);

        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
