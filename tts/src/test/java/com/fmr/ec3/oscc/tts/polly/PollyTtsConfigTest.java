package com.fmr.ec3.oscc.tts.polly;

import com.fmr.ec3.oscc.tts.cache.InMemoryTtsCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PollyTtsConfigTest {

    @Test
    void builderDefaults() {
        PollyTtsConfig config = PollyTtsConfig.builder().build();

        assertThat(config.getDefaultVoiceId()).isEqualTo("Joanna");
        assertThat(config.getEngine()).isEqualTo("neural");
        assertThat(config.getSampleRate()).isEqualTo(8000);
        assertThat(config.getCache()).isNull();
    }

    @Test
    void builderCustomValues() {
        InMemoryTtsCache cache = new InMemoryTtsCache();
        PollyTtsConfig config = PollyTtsConfig.builder()
                .defaultVoiceId("Matthew")
                .engine("standard")
                .sampleRate(16000)
                .cache(cache)
                .build();

        assertThat(config.getDefaultVoiceId()).isEqualTo("Matthew");
        assertThat(config.getEngine()).isEqualTo("standard");
        assertThat(config.getSampleRate()).isEqualTo(16000);
        assertThat(config.getCache()).isSameAs(cache);
    }

    @Test
    void blankVoiceIdThrows() {
        assertThatThrownBy(() -> PollyTtsConfig.builder()
                .defaultVoiceId("  ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultVoiceId");
    }

    @Test
    void nullVoiceIdThrows() {
        assertThatThrownBy(() -> PollyTtsConfig.builder()
                .defaultVoiceId(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultVoiceId");
    }

    @Test
    void blankEngineThrows() {
        assertThatThrownBy(() -> PollyTtsConfig.builder()
                .engine("  ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("engine");
    }

    @Test
    void nullEngineThrows() {
        assertThatThrownBy(() -> PollyTtsConfig.builder()
                .engine(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("engine");
    }

    @Test
    void negativeSampleRateThrows() {
        assertThatThrownBy(() -> PollyTtsConfig.builder()
                .sampleRate(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleRate");
    }

    @Test
    void zeroSampleRateThrows() {
        assertThatThrownBy(() -> PollyTtsConfig.builder()
                .sampleRate(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleRate");
    }
}
