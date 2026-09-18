package com.stegomsg.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private final CryptoService crypto = new CryptoService();

    @Test
    void roundTripRecoversPlaintext() throws Exception {
        SecretKey key = crypto.generateMessageKey();
        byte[] plaintext = "Hello, are you available at 7 PM?".getBytes(StandardCharsets.UTF_8);

        CryptoService.EncryptedPayload encrypted = crypto.encrypt(plaintext, key);
        byte[] decrypted = crypto.decrypt(encrypted.nonce(), encrypted.ciphertextAndTag(), key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void nonceIs96Bits() {
        SecretKey key = crypto.generateMessageKey();
        CryptoService.EncryptedPayload encrypted = crypto.encrypt("x".getBytes(StandardCharsets.UTF_8), key);
        assertEquals(12, encrypted.nonce().length);
    }

    @Test
    void tamperedCiphertextFailsAuthentication() {
        SecretKey key = crypto.generateMessageKey();
        byte[] plaintext = "sensitive message".getBytes(StandardCharsets.UTF_8);
        CryptoService.EncryptedPayload encrypted = crypto.encrypt(plaintext, key);

        byte[] tampered = encrypted.ciphertextAndTag().clone();
        tampered[0] ^= 0x01;

        assertThrows(CryptoService.MessageIntegrityException.class,
                () -> crypto.decrypt(encrypted.nonce(), tampered, key));
    }

    @Test
    void wrongKeyFailsAuthentication() {
        SecretKey key = crypto.generateMessageKey();
        SecretKey wrongKey = crypto.generateMessageKey();
        CryptoService.EncryptedPayload encrypted = crypto.encrypt("secret".getBytes(StandardCharsets.UTF_8), key);

        assertThrows(CryptoService.MessageIntegrityException.class,
                () -> crypto.decrypt(encrypted.nonce(), encrypted.ciphertextAndTag(), wrongKey));
    }

    @Test
    void eachEncryptionUsesAFreshNonce() {
        SecretKey key = crypto.generateMessageKey();
        byte[] plaintext = "same message".getBytes(StandardCharsets.UTF_8);
        CryptoService.EncryptedPayload first = crypto.encrypt(plaintext, key);
        CryptoService.EncryptedPayload second = crypto.encrypt(plaintext, key);
        assertFalse(java.util.Arrays.equals(first.nonce(), second.nonce()),
                "Nonces must never repeat under the same key (GCM security requirement)");
    }
}
