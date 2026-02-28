package com.fmr.ec3.oscc.audiocapture.codec;

/**
 * G.711 A-law to 16-bit signed PCM decoder (ITU-T G.711).
 */
public class ALawCodec implements AudioCodec {

    private static final short[] ALAW_TO_PCM = buildTable();

    private static short[] buildTable() {
        short[] table = new short[256];
        for (int i = 0; i < 256; i++) {
            int alaw = i ^ 0x55;
            int sign = alaw & 0x80;
            int exponent = (alaw >> 4) & 0x07;
            int mantissa = alaw & 0x0F;
            int magnitude;
            if (exponent == 0) {
                magnitude = (mantissa << 1) + 1;
                magnitude <<= 3;
            } else {
                mantissa |= 0x10;
                magnitude = mantissa << (exponent + 2);
                magnitude += 1 << (exponent + 2);
            }
            table[i] = (short) (sign != 0 ? -magnitude : magnitude);
        }
        return table;
    }

    @Override
    public byte[] decode(byte[] data, int offset, int length) {
        byte[] pcm = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            short sample = ALAW_TO_PCM[data[offset + i] & 0xFF];
            int idx = i * 2;
            pcm[idx] = (byte) (sample & 0xFF);
            pcm[idx + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }
}
