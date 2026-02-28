package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.delivery.FileSystemDeliveryTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemDeliveryTargetTest {

    @TempDir
    Path tempDir;

    @Test
    void writesFileAndReturnsUrl() throws IOException {
        FileSystemDeliveryTarget target = new FileSystemDeliveryTarget(tempDir, "/audio");
        byte[] wavData = {1, 2, 3, 4, 5};

        String url = target.deliver(wavData, "session-1", "step-1");

        assertThat(url).isEqualTo("/audio/step-1.wav");
        Path written = tempDir.resolve("step-1.wav");
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readAllBytes(written)).isEqualTo(wavData);
    }

    @Test
    void atomicWrite() throws IOException {
        FileSystemDeliveryTarget target = new FileSystemDeliveryTarget(tempDir, "/audio");
        byte[] wavData = {10, 20, 30};

        target.deliver(wavData, "s1", "step-2");

        // temp file should not remain
        assertThat(Files.exists(tempDir.resolve("step-2.wav.tmp"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("step-2.wav"))).isTrue();
    }

    @Test
    void nonexistentDirectoryThrows() {
        Path badDir = tempDir.resolve("nonexistent");
        FileSystemDeliveryTarget target = new FileSystemDeliveryTarget(badDir, "/audio");

        assertThatThrownBy(() -> target.deliver(new byte[]{1}, "s1", "step-3"))
                .isInstanceOf(IOException.class);
    }
}
