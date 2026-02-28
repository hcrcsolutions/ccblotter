package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.codec.PcmCodec;
import com.fmr.ec3.oscc.audiocapture.delivery.AudioDeliveryTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioCaptureConfigTest {

    private final AudioDeliveryTarget dummyTarget = (wavData, sessionId, stepId) -> "url";

    @Test
    void builderDefaults() {
        AudioCaptureConfig config = AudioCaptureConfig.builder()
                .codec(new PcmCodec())
                .deliveryTarget(dummyTarget)
                .build();

        assertThat(config.getCodec()).isInstanceOf(PcmCodec.class);
        assertThat(config.getDeliveryTarget()).isSameAs(dummyTarget);
        assertThat(config.getMaxCaptureDurationSeconds()).isEqualTo(300);
        assertThat(config.getBufferCapacityBytes()).isEqualTo(320_000);
        assertThat(config.getThreadPool()).isNotNull();
        config.shutdown();
    }

    @Test
    void customValues() {
        AudioCaptureConfig config = AudioCaptureConfig.builder()
                .codec(new PcmCodec())
                .deliveryTarget(dummyTarget)
                .threadPoolSize(2)
                .maxCaptureDurationSeconds(60)
                .bufferCapacityBytes(100_000)
                .build();

        assertThat(config.getMaxCaptureDurationSeconds()).isEqualTo(60);
        assertThat(config.getBufferCapacityBytes()).isEqualTo(100_000);
        config.shutdown();
    }

    @Test
    void missingCodecThrows() {
        assertThatThrownBy(() -> AudioCaptureConfig.builder()
                .deliveryTarget(dummyTarget)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("codec");
    }

    @Test
    void missingDeliveryTargetThrows() {
        assertThatThrownBy(() -> AudioCaptureConfig.builder()
                .codec(new PcmCodec())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deliveryTarget");
    }

    @Test
    void invalidThreadPoolSizeThrows() {
        assertThatThrownBy(() -> AudioCaptureConfig.builder()
                .codec(new PcmCodec())
                .deliveryTarget(dummyTarget)
                .threadPoolSize(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threadPoolSize");
    }
}
