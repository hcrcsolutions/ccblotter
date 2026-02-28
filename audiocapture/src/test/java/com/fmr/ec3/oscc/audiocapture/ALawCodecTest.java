package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.codec.ALawCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ALawCodecTest {

    private final ALawCodec codec = new ALawCodec();

    @Test
    void outputLengthIsDoubleInput() {
        byte[] input = new byte[160];
        byte[] pcm = codec.decode(input, 0, input.length);
        assertThat(pcm).hasSize(320);
    }

    @Test
    void silenceByte() {
        // A-law silence is 0xD5 (alternating pattern after XOR with 0x55 gives 0x80)
        byte[] input = {(byte) 0xD5};
        byte[] pcm = codec.decode(input, 0, 1);
        short sample = (short) ((pcm[1] & 0xFF) << 8 | (pcm[0] & 0xFF));
        assertThat(Math.abs(sample)).isLessThan(100);
    }

    @Test
    void knownPositiveValue() {
        // A-law byte where sign bit (after XOR) is 0 → positive sample
        byte[] input = {0x55}; // XOR 0x55 → 0x00, sign=0, positive
        byte[] pcm = codec.decode(input, 0, 1);
        short sample = (short) ((pcm[1] & 0xFF) << 8 | (pcm[0] & 0xFF));
        assertThat(sample).isPositive();
    }

    @Test
    void knownNegativeValue() {
        // A-law byte where sign bit (after XOR) is 1 → negative sample
        byte[] input = {(byte) 0xD5}; // XOR 0x55 → 0x80, sign=1, negative
        byte[] pcm = codec.decode(input, 0, 1);
        short sample = (short) ((pcm[1] & 0xFF) << 8 | (pcm[0] & 0xFF));
        assertThat(sample).isNegative();
    }

    @Test
    void offsetAndLength() {
        byte[] input = {0x10, 0x20, 0x30, 0x40};
        byte[] pcm = codec.decode(input, 1, 2);
        assertThat(pcm).hasSize(4);

        byte[] directDecode = codec.decode(new byte[]{0x20, 0x30}, 0, 2);
        assertThat(pcm).isEqualTo(directDecode);
    }

    @Test
    void emptyInput() {
        byte[] pcm = codec.decode(new byte[0], 0, 0);
        assertThat(pcm).isEmpty();
    }
}
