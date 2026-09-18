package com.stegomsg.integration;

import com.stegomsg.security.CryptoService;
import com.stegomsg.security.KeyManager;
import com.stegomsg.stego.CapacityCalculator;
import com.stegomsg.stego.SteganographyService;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the exact send/receive pipeline from ARCHITECTURE.md sections 32/33 without
 * touching the database or UI layers: encrypt -> wrap key -> embed -> extract -> unwrap
 * -> decrypt, end to end, using only the security and stego packages directly. This is
 * the test that gives the strongest confidence the cryptographic design actually works
 * as specified, independent of persistence or UI wiring.
 */
class EndToEndPipelineTest {

    private final CryptoService crypto = new CryptoService();
    private final KeyManager keyManager = new KeyManager();
    private final SteganographyService stego = new SteganographyService();
    private final CapacityCalculator capacityCalculator = new CapacityCalculator();

    @Test
    void recipientRecoversOriginalPlaintextFromStegoImage() throws Exception {
        KeyPair recipientKeyPair = keyManager.generateUserKeyPair();
        String messageText = "Meet at the usual place, 9pm.";
        byte[] plaintext = messageText.getBytes(StandardCharsets.UTF_8);

        // --- sender side ---
        SecretKey aesKey = crypto.generateMessageKey();
        CryptoService.EncryptedPayload encrypted = crypto.encrypt(plaintext, aesKey);
        byte[] wrappedKey = keyManager.wrapMessageKey(aesKey, recipientKeyPair.getPublic());
        byte[] wirePayload = frame(wrappedKey, encrypted.nonce(), encrypted.ciphertextAndTag());

        BufferedImage carrier = randomImage(400, 300);
        long capacity = capacityCalculator.maxPayloadBytes(400, 300, 1);
        assertTrue(wirePayload.length + CapacityCalculator.LENGTH_HEADER_BYTES <= capacity,
                "Test image must have enough capacity for this payload");

        BufferedImage stegoImage = stego.embed(carrier, wirePayload, 1);

        // --- receiver side ---
        byte[] extractedWire = stego.extract(stegoImage, 1);
        ByteArrayInputStream in = new ByteArrayInputStream(extractedWire);
        int keyLen = (in.read() << 8) | in.read();
        byte[] extractedWrappedKey = in.readNBytes(keyLen);
        byte[] extractedNonce = in.readNBytes(CryptoService.NONCE_LENGTH_BYTES);
        byte[] extractedCiphertext = in.readAllBytes();

        SecretKey recoveredKey = keyManager.unwrapMessageKey(extractedWrappedKey, recipientKeyPair.getPrivate());
        byte[] recoveredPlaintext = crypto.decrypt(extractedNonce, extractedCiphertext, recoveredKey);

        assertEquals(messageText, new String(recoveredPlaintext, StandardCharsets.UTF_8));
    }

    @Test
    void tamperedStegoImageIsRejectedAtDecryptionNotSilentlyCorrupted() throws Exception {
        KeyPair recipientKeyPair = keyManager.generateUserKeyPair();
        byte[] plaintext = "do not tamper with me".getBytes(StandardCharsets.UTF_8);

        SecretKey aesKey = crypto.generateMessageKey();
        CryptoService.EncryptedPayload encrypted = crypto.encrypt(plaintext, aesKey);
        byte[] wrappedKey = keyManager.wrapMessageKey(aesKey, recipientKeyPair.getPublic());
        byte[] wirePayload = frame(wrappedKey, encrypted.nonce(), encrypted.ciphertextAndTag());

        BufferedImage carrier = randomImage(400, 300);
        BufferedImage stegoImage = stego.embed(carrier, wirePayload, 1);

        // Flip a low bit in a pixel we know carries payload data (top-left corner).
        int argb = stegoImage.getRGB(0, 0);
        stegoImage.setRGB(0, 0, argb ^ 0x00000001);

        byte[] extractedWire = stego.extract(stegoImage, 1);
        ByteArrayInputStream in = new ByteArrayInputStream(extractedWire);
        int keyLen = (in.read() << 8) | in.read();
        byte[] extractedWrappedKey = in.readNBytes(keyLen);
        byte[] extractedNonce = in.readNBytes(CryptoService.NONCE_LENGTH_BYTES);
        byte[] extractedCiphertext = in.readAllBytes();

        SecretKey recoveredKey = keyManager.unwrapMessageKey(extractedWrappedKey, recipientKeyPair.getPrivate());
        assertThrows(CryptoService.MessageIntegrityException.class,
                () -> crypto.decrypt(extractedNonce, extractedCiphertext, recoveredKey));
    }

    private byte[] frame(byte[] wrappedKey, byte[] nonce, byte[] ciphertextAndTag) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((wrappedKey.length >>> 8) & 0xFF);
        out.write(wrappedKey.length & 0xFF);
        out.write(wrappedKey);
        out.write(nonce);
        out.write(ciphertextAndTag);
        return out.toByteArray();
    }

    private BufferedImage randomImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, 0xFF000000 | (r.nextInt(256) << 16) | (r.nextInt(256) << 8) | r.nextInt(256));
            }
        }
        return img;
    }
}
