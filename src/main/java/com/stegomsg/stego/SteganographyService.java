package com.stegomsg.stego;

import java.awt.image.BufferedImage;

/**
 * Least-significant-bit (LSB) steganography over the R, G, B channels of a PNG carrier.
 *
 * IMPORTANT LIMITATIONS (surfaced to the user in the UI's "Advanced Info" panel, per the
 * architecture doc — steganography is a privacy layer, not encryption, and is NOT claimed
 * to be undetectable):
 *   - This is detectable by statistical steganalysis (chi-square attacks, RS analysis) if
 *     an adversary suspects the specific image and has tooling for it. It hides data from
 *     casual/automated inspection, it does not defeat a dedicated forensic analysis.
 *   - Any lossy re-encoding of the resulting image (e.g. re-saving as JPEG, or many chat
 *     apps' image-compression pipelines) will destroy the embedded LSBs. The carrier and
 *     the transport MUST preserve the image byte-for-byte (PNG, sent as a file/attachment,
 *     not re-compressed).
 *   - Capacity is finite and mathematically fixed by image size — see CapacityCalculator,
 *     which this class's bit layout must always match exactly.
 *
 * The payload actually written to the image is [4-byte big-endian length][payload bytes],
 * matching CapacityCalculator.LENGTH_HEADER_BYTES. Everything inside "payload bytes" here
 * (wrapped key, nonce, ciphertext+tag) is opaque to this class — encryption happens one
 * layer up, in MessageService, before this class ever sees the bytes.
 */
public final class SteganographyService {

    /**
     * Embeds {@code payload} into a copy of {@code carrier}. Does not mutate the input image.
     *
     * @throws IllegalArgumentException if the payload (plus its length header) does not
     *         fit in the carrier at the requested bits-per-channel — callers should check
     *         with CapacityCalculator BEFORE calling this, so this is a defensive backstop,
     *         not the primary UX signal.
     */
    public BufferedImage embed(BufferedImage carrier, byte[] payload, int bitsPerChannel) {
        byte[] framed = frame(payload);
        long capacityBytes = new CapacityCalculator()
                .maxPayloadBytes(carrier.getWidth(), carrier.getHeight(), bitsPerChannel);
        if (framed.length > capacityBytes) {
            throw new IllegalArgumentException(
                    "Payload (" + framed.length + " bytes) exceeds carrier capacity (" + capacityBytes + " bytes)");
        }

        BufferedImage stego = new BufferedImage(carrier.getWidth(), carrier.getHeight(), BufferedImage.TYPE_INT_ARGB);
        BitCursor bits = new BitCursor(framed);
        int mask = (1 << bitsPerChannel) - 1;

        outer:
        for (int y = 0; y < carrier.getHeight(); y++) {
            for (int x = 0; x < carrier.getWidth(); x++) {
                int argb = carrier.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                if (bits.hasMore()) {
                    r = (r & ~mask) | bits.take(bitsPerChannel);
                }
                if (bits.hasMore()) {
                    g = (g & ~mask) | bits.take(bitsPerChannel);
                }
                if (bits.hasMore()) {
                    b = (b & ~mask) | bits.take(bitsPerChannel);
                }

                stego.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);

                if (!bits.hasMore() && x == carrier.getWidth() - 1) {
                    // finished embedding but must still copy remaining pixels untouched
                }
            }
        }
        // Copy any remaining untouched pixels (if payload finished early) so the rest of
        // the image is preserved exactly.
        copyRemaining(carrier, stego, bits);
        return stego;
    }

    /** Reads back the payload previously embedded by {@link #embed}. */
    public byte[] extract(BufferedImage stego, int bitsPerChannel) {
        int mask = (1 << bitsPerChannel) - 1;
        int headerBits = CapacityCalculator.LENGTH_HEADER_BYTES * 8;

        BitAccumulator header = new BitAccumulator(headerBits);
        PixelWalker walker = new PixelWalker(stego);
        fill(header, walker, bitsPerChannel, mask);

        int payloadLength = bytesToInt(header.toBytes());
        if (payloadLength < 0) {
            throw new IllegalStateException("Corrupted or absent steganographic payload (invalid length header)");
        }

        BitAccumulator body = new BitAccumulator(payloadLength * 8);
        fill(body, walker, bitsPerChannel, mask);
        return body.toBytes();
    }

    // ---- internal helpers -------------------------------------------------

    private byte[] frame(byte[] payload) {
        byte[] framed = new byte[CapacityCalculator.LENGTH_HEADER_BYTES + payload.length];
        framed[0] = (byte) (payload.length >>> 24);
        framed[1] = (byte) (payload.length >>> 16);
        framed[2] = (byte) (payload.length >>> 8);
        framed[3] = (byte) payload.length;
        System.arraycopy(payload, 0, framed, CapacityCalculator.LENGTH_HEADER_BYTES, payload.length);
        return framed;
    }

    private int bytesToInt(byte[] b) {
        return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
    }

    private void copyRemaining(BufferedImage carrier, BufferedImage stego, BitCursor bits) {
        if (bits.hasMore()) {
            return; // shouldn't happen given the capacity check in embed()
        }
        // setRGB above already wrote every pixel in the loop (with untouched channels
        // once bits ran out), so there is nothing left to copy. Kept as an explicit
        // no-op method so the intent is documented and testable.
    }

    private void fill(BitAccumulator acc, PixelWalker walker, int bitsPerChannel, int mask) {
        while (acc.hasRoom()) {
            int channelValue = walker.nextChannel();
            acc.add(channelValue & mask, bitsPerChannel);
        }
    }

    /** Writes successive {@code bitsPerChannel}-sized chunks of a byte array out, MSB-first. */
    private static final class BitCursor {
        private final byte[] data;
        private int bitPos = 0;

        BitCursor(byte[] data) {
            this.data = data;
        }

        boolean hasMore() {
            return bitPos < data.length * 8;
        }

        /** Takes the next {@code n} bits (n <= 8) as the low bits of the returned int. */
        int take(int n) {
            int value = 0;
            for (int i = 0; i < n; i++) {
                value <<= 1;
                if (bitPos < data.length * 8) {
                    int byteIndex = bitPos / 8;
                    int bitInByte = 7 - (bitPos % 8);
                    int bit = (data[byteIndex] >> bitInByte) & 1;
                    value |= bit;
                    bitPos++;
                }
            }
            return value;
        }
    }

    /** Walks an image's R, G, B channels in the same pixel order embed() used. */
    private static final class PixelWalker {
        private final BufferedImage image;
        private int x = 0;
        private int y = 0;
        private int channel = 0; // 0=R, 1=G, 2=B

        PixelWalker(BufferedImage image) {
            this.image = image;
        }

        int nextChannel() {
            if (y >= image.getHeight()) {
                throw new IllegalStateException("Ran off the end of the image while extracting payload");
            }
            int argb = image.getRGB(x, y);
            int value = switch (channel) {
                case 0 -> (argb >> 16) & 0xFF;
                case 1 -> (argb >> 8) & 0xFF;
                default -> argb & 0xFF;
            };
            channel++;
            if (channel == 3) {
                channel = 0;
                x++;
                if (x == image.getWidth()) {
                    x = 0;
                    y++;
                }
            }
            return value;
        }
    }

    /** Accumulates bits (MSB-first) into a fixed-size byte array as they're read from pixels. */
    private static final class BitAccumulator {
        private final byte[] out;
        private int bitPos = 0;
        private final int totalBits;

        BitAccumulator(int totalBits) {
            this.totalBits = totalBits;
            this.out = new byte[(totalBits + 7) / 8];
        }

        boolean hasRoom() {
            return bitPos < totalBits;
        }

        void add(int bits, int count) {
            for (int i = count - 1; i >= 0 && bitPos < totalBits; i--) {
                int bit = (bits >> i) & 1;
                int byteIndex = bitPos / 8;
                int bitInByte = 7 - (bitPos % 8);
                out[byteIndex] |= (byte) (bit << bitInByte);
                bitPos++;
            }
        }

        byte[] toBytes() {
            return out;
        }
    }
}
