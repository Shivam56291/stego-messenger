package com.stegomsg.stego;

/**
 * The single source of truth for steganographic capacity math. Both the live UI
 * indicator and the actual send pipeline call this class, so what the user sees
 * before sending is guaranteed to match what will actually happen — no separate
 * "estimate" that can drift from reality.
 *
 * ENCODING SCHEME (must match SteganographyService):
 *   - Carrier: PNG loaded as an ARGB BufferedImage, we use the 3 colour channels
 *     (R, G, B) per pixel — the alpha channel is left untouched to avoid visible
 *     transparency artefacts and because many viewers/re-encoders normalise alpha.
 *   - Bits per channel: LSB_BITS_PER_CHANNEL least-significant bits of each of the
 *     3 channels carry payload data. Default is 1 bit/channel (classic LSB), which
 *     is visually imperceptible; 2 bits/channel roughly doubles capacity at a small,
 *     still generally imperceptible, statistical cost (easier to detect via
 *     steganalysis — see the security notes in ARCHITECTURE.md).
 *   - Framing: a fixed-size 4-byte big-endian length header precedes the payload so
 *     the extractor knows exactly how many bytes to read back out — we never scan
 *     for a delimiter inside attacker-controlled ciphertext.
 *
 * WIRE PAYLOAD LAYOUT (what actually gets embedded, produced by MessageService):
 *   [2 bytes: wrapped-AES-key length] [wrapped AES key (RSA-2048/OAEP => 256 bytes)]
 *   [12 bytes: GCM nonce] [ciphertext + 16-byte GCM tag]
 *
 * That whole structure is then prefixed by the 4-byte length header described above
 * before embedding. HEADER_OVERHEAD_BYTES below accounts for the 4-byte length header;
 * WRAPPED_KEY_FIELD_BYTES + RSA_WRAPPED_KEY_BYTES account for the key-wrapping field;
 * NONCE_BYTES and GCM_TAG_BYTES account for the AEAD framing. Together these let the
 * calculator report a real, byte-accurate capacity rather than a rough guess.
 */
public final class CapacityCalculator {

    public static final int COLOR_CHANNELS = 3; // R, G, B (alpha untouched)
    public static final int DEFAULT_BITS_PER_CHANNEL = 1;

    public static final int LENGTH_HEADER_BYTES = 4;
    public static final int WRAPPED_KEY_FIELD_BYTES = 2;
    public static final int RSA2048_WRAPPED_KEY_BYTES = 256; // RSA-2048 output size, fixed
    public static final int GCM_NONCE_BYTES = 12;
    public static final int GCM_TAG_BYTES = 16;

    /** Fixed overhead present in every message regardless of plaintext length, in bytes. */
    public static final int FIXED_OVERHEAD_BYTES =
            LENGTH_HEADER_BYTES + WRAPPED_KEY_FIELD_BYTES + RSA2048_WRAPPED_KEY_BYTES
                    + GCM_NONCE_BYTES + GCM_TAG_BYTES;

    public enum Status { SAFE, WARNING, TOO_LARGE }

    public record CapacityReport(
            int widthPx,
            int heightPx,
            int bitsPerChannel,
            long maxPayloadBytes,
            long actualPayloadBytes,
            double percentUsed,
            Status status
    ) {
    }

    /**
     * Maximum number of whole bytes that can be embedded in an image of the given
     * dimensions, at the given bits-per-channel setting, using {@link #COLOR_CHANNELS}
     * channels. This is the raw carrier capacity — callers subtract their own protocol
     * overhead (see {@link #actualPayloadBytes}) to know how much is left for the
     * message itself.
     */
    public long maxPayloadBytes(int widthPx, int heightPx, int bitsPerChannel) {
        if (widthPx <= 0 || heightPx <= 0 || bitsPerChannel <= 0 || bitsPerChannel > 8) {
            throw new IllegalArgumentException("Invalid image dimensions or bits-per-channel");
        }
        long totalBits = (long) widthPx * heightPx * COLOR_CHANNELS * bitsPerChannel;
        return totalBits / 8; // whole bytes only; partial trailing bits are unusable
    }

    /**
     * Total on-the-wire payload size for a given UTF-8 plaintext byte length, i.e. what
     * will actually need to fit inside the image once encryption, key-wrapping and
     * framing overhead are added.
     */
    public long actualPayloadBytes(int plaintextByteLength) {
        if (plaintextByteLength < 0) {
            throw new IllegalArgumentException("Plaintext length cannot be negative");
        }
        // AES-GCM ciphertext is the same length as the plaintext; the 16-byte tag is
        // accounted for separately in FIXED_OVERHEAD_BYTES.
        return (long) plaintextByteLength + FIXED_OVERHEAD_BYTES;
    }

    /**
     * Produces the full live report the UI binds to: given an image and the message
     * currently typed, what is the maximum capacity, how much of it is used, and is it
     * safe to send.
     */
    public CapacityReport report(int widthPx, int heightPx, int bitsPerChannel, int plaintextByteLength) {
        long max = maxPayloadBytes(widthPx, heightPx, bitsPerChannel);
        long actual = actualPayloadBytes(plaintextByteLength);
        double percent = max == 0 ? 100.0 : (actual * 100.0) / max;
        Status status;
        if (actual > max) {
            status = Status.TOO_LARGE;
        } else if (percent >= 85.0) {
            status = Status.WARNING;
        } else {
            status = Status.SAFE;
        }
        return new CapacityReport(widthPx, heightPx, bitsPerChannel, max, actual, percent, status);
    }
}
