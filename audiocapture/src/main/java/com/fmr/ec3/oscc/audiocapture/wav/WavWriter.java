package com.fmr.ec3.oscc.audiocapture.wav;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes a RIFF/WAVE file with a 44-byte header for 8 kHz, 16-bit, mono PCM audio.
 */
public final class WavWriter {

    private static final int HEADER_SIZE = 44;
    private static final int SAMPLE_RATE = 8000;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int NUM_CHANNELS = 1;
    private static final int BYTE_RATE = SAMPLE_RATE * NUM_CHANNELS * BITS_PER_SAMPLE / 8;
    private static final int BLOCK_ALIGN = NUM_CHANNELS * BITS_PER_SAMPLE / 8;

    private WavWriter() {
    }

    public static void write(OutputStream out, byte[] pcmData, int offset, int length)
            throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        int dataSize = length;
        int fileSize = dataSize + HEADER_SIZE - 8;

        // RIFF chunk descriptor
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        writeInt32LE(header, 4, fileSize);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';

        // fmt sub-chunk
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        writeInt32LE(header, 16, 16);        // sub-chunk size
        writeInt16LE(header, 20, 1);         // audio format: PCM
        writeInt16LE(header, 22, NUM_CHANNELS);
        writeInt32LE(header, 24, SAMPLE_RATE);
        writeInt32LE(header, 28, BYTE_RATE);
        writeInt16LE(header, 32, BLOCK_ALIGN);
        writeInt16LE(header, 34, BITS_PER_SAMPLE);

        // data sub-chunk
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        writeInt32LE(header, 40, dataSize);

        out.write(header);
        out.write(pcmData, offset, length);
    }

    public static int totalFileSize(int pcmDataLength) {
        return HEADER_SIZE + pcmDataLength;
    }

    public static long durationMs(int pcmDataLength) {
        return (long) pcmDataLength * 1000 / BYTE_RATE;
    }

    private static void writeInt32LE(byte[] buf, int pos, int value) {
        buf[pos] = (byte) (value & 0xFF);
        buf[pos + 1] = (byte) ((value >> 8) & 0xFF);
        buf[pos + 2] = (byte) ((value >> 16) & 0xFF);
        buf[pos + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeInt16LE(byte[] buf, int pos, int value) {
        buf[pos] = (byte) (value & 0xFF);
        buf[pos + 1] = (byte) ((value >> 8) & 0xFF);
    }
}
