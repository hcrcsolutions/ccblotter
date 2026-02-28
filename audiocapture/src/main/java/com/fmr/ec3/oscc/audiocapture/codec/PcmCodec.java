package com.fmr.ec3.oscc.audiocapture.codec;

import java.util.Arrays;

/**
 * Passthrough codec for raw 16-bit signed PCM audio.
 */
public class PcmCodec implements AudioCodec {

    @Override
    public byte[] decode(byte[] data, int offset, int length) {
        return Arrays.copyOfRange(data, offset, offset + length);
    }
}
