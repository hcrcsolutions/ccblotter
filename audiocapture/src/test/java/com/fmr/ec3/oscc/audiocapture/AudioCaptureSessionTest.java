package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.codec.MuLawCodec;
import com.fmr.ec3.oscc.audiocapture.codec.PcmCodec;
import com.fmr.ec3.oscc.audiocapture.delivery.FileSystemDeliveryTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AudioCaptureSessionTest {

    @TempDir
    Path tempDir;

    private AudioCaptureConfig config;

    @BeforeEach
    void setUp() {
        config = AudioCaptureConfig.builder()
                .codec(new PcmCodec())
                .deliveryTarget(new FileSystemDeliveryTarget(tempDir, "/audio"))
                .threadPoolSize(2)
                .build();
    }

    @AfterEach
    void tearDown() {
        config.shutdown();
    }

    @Test
    void fullCaptureFlow() throws Exception {
        AudioCaptureSession session = AudioCaptureSession.start(config, "sess-1", "step-1");

        byte[] frame1 = new byte[320]; // 20ms of 8kHz 16-bit mono
        byte[] frame2 = new byte[320];
        session.write(frame1, 0, frame1.length);
        session.write(frame2, 0, frame2.length);

        CaptureResult result = session.stop().get(5, TimeUnit.SECONDS);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.getUrl()).isEqualTo("/audio/step-1.wav");
        assertThat(result.getSessionId()).isEqualTo("sess-1");
        assertThat(result.getStepId()).isEqualTo("step-1");
        assertThat(result.getDurationMs()).isEqualTo(40); // 640 bytes / 16000 bytes/sec * 1000
        assertThat(result.getFileSizeBytes()).isEqualTo(640 + 44); // PCM + WAV header

        Path wavFile = tempDir.resolve("step-1.wav");
        assertThat(Files.exists(wavFile)).isTrue();
        assertThat(Files.size(wavFile)).isEqualTo(684);
    }

    @Test
    void fullCaptureFlowWithMuLaw() throws Exception {
        AudioCaptureConfig muLawConfig = AudioCaptureConfig.builder()
                .codec(new MuLawCodec())
                .deliveryTarget(new FileSystemDeliveryTarget(tempDir, "/audio"))
                .threadPoolSize(1)
                .build();

        try {
            AudioCaptureSession session = AudioCaptureSession.start(muLawConfig, "sess-2", "step-2");

            byte[] frame = new byte[160]; // 20ms of 8kHz mu-law (1 byte per sample)
            session.write(frame, 0, frame.length);

            CaptureResult result = session.stop().get(5, TimeUnit.SECONDS);

            assertThat(result.isEmpty()).isFalse();
            // 160 mu-law bytes → 320 PCM bytes → 320/16000*1000 = 20ms
            assertThat(result.getDurationMs()).isEqualTo(20);
        } finally {
            muLawConfig.shutdown();
        }
    }

    @Test
    void emptyCapture() throws Exception {
        AudioCaptureSession session = AudioCaptureSession.start(config, "sess-3", "step-3");

        CaptureResult result = session.stop().get(5, TimeUnit.SECONDS);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getUrl()).isNull();
        assertThat(result.getDurationMs()).isEqualTo(0);
    }

    @Test
    void cancel() {
        AudioCaptureSession session = AudioCaptureSession.start(config, "sess-4", "step-4");
        session.write(new byte[320], 0, 320);

        session.cancel();

        // After cancel, further writes are silently ignored
        session.write(new byte[320], 0, 320);
    }

    @Test
    void writeAfterStop() throws Exception {
        AudioCaptureSession session = AudioCaptureSession.start(config, "sess-5", "step-5");
        session.write(new byte[320], 0, 320);

        CompletableFuture<CaptureResult> future = session.stop();

        // Writes after stop are silently ignored
        session.write(new byte[320], 0, 320);

        CaptureResult result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void concurrentSessions() throws Exception {
        int sessionCount = 5;
        List<CompletableFuture<CaptureResult>> futures = new ArrayList<>();

        for (int i = 0; i < sessionCount; i++) {
            AudioCaptureSession session = AudioCaptureSession.start(
                    config, "sess-c", "step-c-" + i);
            session.write(new byte[320], 0, 320);
            futures.add(session.stop());
        }

        for (int i = 0; i < sessionCount; i++) {
            CaptureResult result = futures.get(i).get(5, TimeUnit.SECONDS);
            assertThat(result.isEmpty()).isFalse();
            assertThat(result.getUrl()).isEqualTo("/audio/step-c-" + i + ".wav");
        }
    }

    @Test
    void maxDurationTruncation() throws Exception {
        AudioCaptureConfig smallConfig = AudioCaptureConfig.builder()
                .codec(new PcmCodec())
                .deliveryTarget(new FileSystemDeliveryTarget(tempDir, "/audio"))
                .bufferCapacityBytes(100)
                .threadPoolSize(1)
                .build();

        try {
            AudioCaptureSession session = AudioCaptureSession.start(smallConfig, "sess-6", "step-6");

            // Write more than the buffer capacity
            session.write(new byte[80], 0, 80);
            session.write(new byte[80], 0, 80);

            CaptureResult result = session.stop().get(5, TimeUnit.SECONDS);

            // Should be truncated to buffer capacity
            assertThat(result.getFileSizeBytes()).isEqualTo(100 + 44);
        } finally {
            smallConfig.shutdown();
        }
    }
}
