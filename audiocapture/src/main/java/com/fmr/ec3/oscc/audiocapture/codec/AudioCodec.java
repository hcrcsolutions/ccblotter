package com.fmr.ec3.oscc.audiocapture.codec;

/**
 * Decodes audio samples to 16-bit signed PCM.
 */
public interface AudioCodec {

    byte[] decode(byte[] data, int offset, int length);

    default int getSampleRate() {
        return 8000;
    }

    default int getBitsPerSample() {
        return 16;
    }

    default int getChannels() {
        return 1;
    }
}
