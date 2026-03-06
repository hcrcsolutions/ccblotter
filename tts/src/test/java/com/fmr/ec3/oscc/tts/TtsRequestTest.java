package com.fmr.ec3.oscc.tts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TtsRequestTest {

    @Test
    void builderDefaults() {
        TtsRequest request = TtsRequest.builder("Hello").build();

        assertThat(request.getText()).isEqualTo("Hello");
        assertThat(request.getVoiceId()).isNull();
        assertThat(request.isCacheable()).isFalse();
    }

    @Test
    void builderWithAllFields() {
        TtsRequest request = TtsRequest.builder("Hello")
                .voiceId("Matthew")
                .cacheable(true)
                .build();

        assertThat(request.getText()).isEqualTo("Hello");
        assertThat(request.getVoiceId()).isEqualTo("Matthew");
        assertThat(request.isCacheable()).isTrue();
    }

    @Test
    void nullTextThrows() {
        assertThatThrownBy(() -> TtsRequest.builder(null).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void blankTextThrows() {
        assertThatThrownBy(() -> TtsRequest.builder("   ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }
}
