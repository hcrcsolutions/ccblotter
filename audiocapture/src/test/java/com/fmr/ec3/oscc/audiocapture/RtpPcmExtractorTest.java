package com.fmr.ec3.oscc.audiocapture;

import com.fmr.ec3.oscc.audiocapture.codec.PcmCodec;
import com.fmr.ec3.oscc.audiocapture.codec.RtpPcmExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RtpPcmExtractorTest {

    private final RtpPcmExtractor extractor = new RtpPcmExtractor(new PcmCodec());

    @Test
    void minimalHeader() {
        // V=2, P=0, X=0, CC=0, M=0, PT=0, seq=1, ts=100, ssrc=42
        byte[] rtp = new byte[12 + 4]; // 12 header + 4 payload
        rtp[0] = (byte) 0x80; // V=2
        rtp[1] = 0x00;
        // payload bytes
        rtp[12] = 0x0A;
        rtp[13] = 0x0B;
        rtp[14] = 0x0C;
        rtp[15] = 0x0D;

        byte[] result = extractor.decode(rtp, 0, rtp.length);
        assertThat(result).containsExactly(0x0A, 0x0B, 0x0C, 0x0D);
    }

    @Test
    void withCsrcEntries() {
        // CC=2 → 2 CSRC entries (8 extra bytes)
        byte[] rtp = new byte[12 + 8 + 2]; // header + 2 CSRC + 2 payload
        rtp[0] = (byte) 0x82; // V=2, CC=2
        rtp[1] = 0x00;
        // payload at offset 20
        rtp[20] = 0x42;
        rtp[21] = 0x43;

        byte[] result = extractor.decode(rtp, 0, rtp.length);
        assertThat(result).containsExactly(0x42, 0x43);
    }

    @Test
    void withExtensionHeader() {
        // X=1, extension length=1 (1 * 4 = 4 bytes of extension data)
        byte[] rtp = new byte[12 + 4 + 4 + 3]; // header + ext header(4) + ext data(4) + payload(3)
        rtp[0] = (byte) 0x90; // V=2, X=1, CC=0
        rtp[1] = 0x00;
        // extension header at offset 12
        rtp[12] = 0x00; // profile-specific
        rtp[13] = 0x00;
        rtp[14] = 0x00; // extension length = 1 (in 32-bit words)
        rtp[15] = 0x01;
        // extension data (4 bytes)
        rtp[16] = 0x00;
        rtp[17] = 0x00;
        rtp[18] = 0x00;
        rtp[19] = 0x00;
        // payload at offset 20
        rtp[20] = 0x11;
        rtp[21] = 0x22;
        rtp[22] = 0x33;

        byte[] result = extractor.decode(rtp, 0, rtp.length);
        assertThat(result).containsExactly(0x11, 0x22, 0x33);
    }

    @Test
    void withCsrcAndExtension() {
        // CC=1 + X=1, extension length=1
        byte[] rtp = new byte[12 + 4 + 4 + 4 + 2]; // header + CSRC + ext header + ext data + payload
        rtp[0] = (byte) 0x91; // V=2, X=1, CC=1
        rtp[1] = 0x00;
        // CSRC at 12-15 (4 bytes)
        // extension header at offset 16
        rtp[16] = 0x00;
        rtp[17] = 0x00;
        rtp[18] = 0x00; // extension length = 1
        rtp[19] = 0x01;
        // extension data at 20-23
        // payload at offset 24
        rtp[24] = (byte) 0xAA;
        rtp[25] = (byte) 0xBB;

        byte[] result = extractor.decode(rtp, 0, rtp.length);
        assertThat(result).containsExactly((byte) 0xAA, (byte) 0xBB);
    }

    @Test
    void emptyPayload() {
        // 12-byte header with no payload
        byte[] rtp = new byte[12];
        rtp[0] = (byte) 0x80;

        byte[] result = extractor.decode(rtp, 0, rtp.length);
        assertThat(result).isEmpty();
    }

    @Test
    void tooShortForHeader() {
        byte[] rtp = new byte[8]; // less than 12
        byte[] result = extractor.decode(rtp, 0, rtp.length);
        assertThat(result).isEmpty();
    }
}
