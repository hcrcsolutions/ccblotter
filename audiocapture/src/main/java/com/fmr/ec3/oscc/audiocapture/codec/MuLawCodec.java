package com.fmr.ec3.oscc.audiocapture.codec;

/**
 * G.711 mu-law to 16-bit signed PCM decoder (ITU-T G.711).
 */
public class MuLawCodec implements AudioCodec {

    private static final short[] MULAW_TO_PCM = buildTable();

    private static short[] buildTable() {
        short[] table = new short[256];
        for (int i = 0; i < 256; i++) {
            int mulaw = ~i & 0xFF;
            int sign = mulaw & 0x80;
            int exponent = (mulaw >> 4) & 0x07;
            int mantissa = mulaw & 0x0F;
            int magnitude = ((mantissa << 1) + 33) << (exponent + 2);
            magnitude -= 0x84;
            table[i] = (short) (sign != 0 ? -magnitude : magnitude);
        }
        return table;
    }

    @Override
    public byte[] decode(byte[] data, int offset, int length) {
        byte[] pcm = new byte[length * 2];
        for (int i = 0; i < length; i++) {
            short sample = MULAW_TO_PCM[data[offset + i] & 0xFF];
            int idx = i * 2;
            pcm[idx] = (byte) (sample & 0xFF);
            pcm[idx + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }
}
