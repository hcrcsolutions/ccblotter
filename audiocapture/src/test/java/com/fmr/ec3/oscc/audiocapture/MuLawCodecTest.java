package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.codec.MuLawCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MuLawCodecTest {

    private final MuLawCodec codec = new MuLawCodec();

    @Test
    void outputLengthIsDoubleInput() {
        byte[] input = new byte[160];
        byte[] pcm = codec.decode(input, 0, input.length);
        assertThat(pcm).hasSize(320);
    }

    @Test
    void silenceByte() {
        // mu-law silence is 0xFF (encodes to ~0)
        byte[] input = {(byte) 0xFF};
        byte[] pcm = codec.decode(input, 0, 1);
        short sample = (short) ((pcm[1] & 0xFF) << 8 | (pcm[0] & 0xFF));
        assertThat(Math.abs(sample)).isLessThan(10);
    }

    @Test
    void knownPositiveValue() {
        // mu-law 0x00 encodes the maximum negative magnitude
        byte[] input = {0x00};
        byte[] pcm = codec.decode(input, 0, 1);
        short sample = (short) ((pcm[1] & 0xFF) << 8 | (pcm[0] & 0xFF));
        assertThat(sample).isNegative();
    }

    @Test
    void knownNegativeValue() {
        // mu-law 0x80 encodes the maximum positive magnitude
        byte[] input = {(byte) 0x80};
        byte[] pcm = codec.decode(input, 0, 1);
        short sample = (short) ((pcm[1] & 0xFF) << 8 | (pcm[0] & 0xFF));
        assertThat(sample).isPositive();
    }

    @Test
    void offsetAndLength() {
        byte[] input = {0x10, 0x20, 0x30, 0x40};
        byte[] pcm = codec.decode(input, 1, 2);
        assertThat(pcm).hasSize(4);

        // Verify it decoded bytes at index 1 and 2, not 0 and 1
        byte[] directDecode = codec.decode(new byte[]{0x20, 0x30}, 0, 2);
        assertThat(pcm).isEqualTo(directDecode);
    }

    @Test
    void emptyInput() {
        byte[] pcm = codec.decode(new byte[0], 0, 0);
        assertThat(pcm).isEmpty();
    }
}
