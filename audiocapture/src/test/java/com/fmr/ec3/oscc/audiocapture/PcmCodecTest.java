package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.codec.PcmCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PcmCodecTest {

    private final PcmCodec codec = new PcmCodec();

    @Test
    void passthrough() {
        byte[] input = {1, 2, 3, 4, 5};
        byte[] result = codec.decode(input, 0, input.length);
        assertThat(result).isEqualTo(input);
        assertThat(result).isNotSameAs(input);
    }

    @Test
    void offsetAndLength() {
        byte[] input = {10, 20, 30, 40, 50};
        byte[] result = codec.decode(input, 1, 3);
        assertThat(result).containsExactly(20, 30, 40);
    }

    @Test
    void emptyInput() {
        byte[] result = codec.decode(new byte[0], 0, 0);
        assertThat(result).isEmpty();
    }
}
