package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.wav.WavWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class WavWriterTest {

    @Test
    void headerCorrectness() throws IOException {
        byte[] pcm = new byte[16000]; // 1 second of 8kHz 16-bit mono
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WavWriter.write(out, pcm, 0, pcm.length);
        byte[] wav = out.toByteArray();

        // Total size: 44 header + 16000 data
        assertThat(wav).hasSize(44 + 16000);

        // RIFF header
        assertThat(new String(wav, 0, 4)).isEqualTo("RIFF");
        int chunkSize = readInt32LE(wav, 4);
        assertThat(chunkSize).isEqualTo(wav.length - 8);

        // WAVE format
        assertThat(new String(wav, 8, 4)).isEqualTo("WAVE");

        // fmt sub-chunk
        assertThat(new String(wav, 12, 4)).isEqualTo("fmt ");
        int fmtSize = readInt32LE(wav, 16);
        assertThat(fmtSize).isEqualTo(16);
        int audioFormat = readInt16LE(wav, 20);
        assertThat(audioFormat).isEqualTo(1); // PCM
        int numChannels = readInt16LE(wav, 22);
        assertThat(numChannels).isEqualTo(1);
        int sampleRate = readInt32LE(wav, 24);
        assertThat(sampleRate).isEqualTo(8000);
        int byteRate = readInt32LE(wav, 28);
        assertThat(byteRate).isEqualTo(16000);
        int blockAlign = readInt16LE(wav, 32);
        assertThat(blockAlign).isEqualTo(2);
        int bitsPerSample = readInt16LE(wav, 34);
        assertThat(bitsPerSample).isEqualTo(16);

        // data sub-chunk
        assertThat(new String(wav, 36, 4)).isEqualTo("data");
        int dataSize = readInt32LE(wav, 40);
        assertThat(dataSize).isEqualTo(16000);
    }

    @Test
    void totalFileSize() {
        assertThat(WavWriter.totalFileSize(16000)).isEqualTo(16044);
        assertThat(WavWriter.totalFileSize(0)).isEqualTo(44);
    }

    @Test
    void durationCalculation() {
        // 16000 bytes at 16000 bytes/sec = 1000ms
        assertThat(WavWriter.durationMs(16000)).isEqualTo(1000);
        // 8000 bytes = 500ms
        assertThat(WavWriter.durationMs(8000)).isEqualTo(500);
        assertThat(WavWriter.durationMs(0)).isEqualTo(0);
    }

    @Test
    void emptyData() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WavWriter.write(out, new byte[0], 0, 0);
        byte[] wav = out.toByteArray();
        assertThat(wav).hasSize(44);

        int dataSize = readInt32LE(wav, 40);
        assertThat(dataSize).isEqualTo(0);
    }

    private static int readInt32LE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }

    private static int readInt16LE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8);
    }
}
