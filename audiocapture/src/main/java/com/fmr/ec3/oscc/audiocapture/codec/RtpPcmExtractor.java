package com.fmr.ec3.oscc.audiocapture.codec;

/**
 * Strips RTP headers (RFC 3550) and delegates the payload to an inner codec.
 *
 * <p>RTP header layout (minimum 12 bytes):
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             SSRC                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 */
public class RtpPcmExtractor implements AudioCodec {

    private static final int RTP_FIXED_HEADER_SIZE = 12;

    private final AudioCodec innerCodec;

    public RtpPcmExtractor(AudioCodec innerCodec) {
        this.innerCodec = innerCodec;
    }

    @Override
    public byte[] decode(byte[] data, int offset, int length) {
        if (length < RTP_FIXED_HEADER_SIZE) {
            return new byte[0];
        }

        int firstByte = data[offset] & 0xFF;
        int cc = firstByte & 0x0F;
        boolean hasExtension = (firstByte & 0x10) != 0;

        int headerSize = RTP_FIXED_HEADER_SIZE + cc * 4;

        if (hasExtension) {
            if (length < headerSize + 4) {
                return new byte[0];
            }
            int extensionLength = ((data[offset + headerSize + 2] & 0xFF) << 8)
                    | (data[offset + headerSize + 3] & 0xFF);
            headerSize += 4 + extensionLength * 4;
        }

        int payloadOffset = offset + headerSize;
        int payloadLength = length - headerSize;
        if (payloadLength <= 0) {
            return new byte[0];
        }

        return innerCodec.decode(data, payloadOffset, payloadLength);
    }

    @Override
    public int getSampleRate() {
        return innerCodec.getSampleRate();
    }

    @Override
    public int getBitsPerSample() {
        return innerCodec.getBitsPerSample();
    }

    @Override
    public int getChannels() {
        return innerCodec.getChannels();
    }
}
