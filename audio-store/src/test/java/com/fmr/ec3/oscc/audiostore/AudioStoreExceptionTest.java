package com.fmr.ec3.oscc.audiostore;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioStoreExceptionTest {

    @Test
    void messageOnlyConstructor() {
        AudioStoreException ex = new AudioStoreException("failed");
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructor() {
        RuntimeException cause = new RuntimeException("root");
        AudioStoreException ex = new AudioStoreException("failed", cause);
        assertThat(ex.getMessage()).isEqualTo("failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void isRuntimeException() {
        assertThat(new AudioStoreException("x")).isInstanceOf(RuntimeException.class);
    }
}
