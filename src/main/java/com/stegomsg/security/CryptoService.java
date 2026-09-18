package com.stegomsg.security;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated symmetric encryption for message payloads.
 *
 * Algorithm: AES-256-GCM.
 *   - Confidentiality: AES-256 in counter mode.
 *   - Integrity/authenticity: the 128-bit GCM authentication tag. Any bit-flip in the
 *     ciphertext (accidental corruption OR deliberate tampering, e.g. re-saving the
 *     stego image through a lossy pipeline, or an attacker modifying pixels) causes
 *     decryption to throw AEADBadTagException instead of silently returning garbage.
 *   - Nonce: 96 bits (12 bytes), the size GCM is designed for. Generated fresh per
 *     message via SecureRandomUtil — GCM nonces must NEVER repeat under the same key,
 *     so we also generate a fresh random AES key per message (see KeyManager) rather
 *     than reusing one long-lived per-conversation key with a nonce counter, which
 *     would need careful state management this project doesn't need.
 *
 * This class only ever handles the message-encryption key. It knows nothing about
 * passwords, PINs, session tokens or steganography — see PasswordHasher, KeyManager
 * and SteganographyService for those.
 */
public final class CryptoService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    public static final int KEY_LENGTH_BITS = 256;
    public static final int NONCE_LENGTH_BYTES = 12;
    public static final int TAG_LENGTH_BITS = 128;

    /** Generates a fresh random AES-256 key. One per message — never reused. */
    public SecretKey generateMessageKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(KEY_ALGORITHM);
            generator.init(KEY_LENGTH_BITS, SecureRandomUtil.instance());
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("AES unavailable on this JVM", e);
        }
    }

    public static SecretKey keyFromBytes(byte[] raw) {
        return new SecretKeySpec(raw, KEY_ALGORITHM);
    }

    /** Result of an encryption: the nonce and the ciphertext (which includes the auth tag). */
    public record EncryptedPayload(byte[] nonce, byte[] ciphertextAndTag) {
    }

    public EncryptedPayload encrypt(byte[] plaintext, SecretKey key) {
        try {
            byte[] nonce = SecureRandomUtil.randomBytes(NONCE_LENGTH_BYTES);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertextAndTag = cipher.doFinal(plaintext);
            return new EncryptedPayload(nonce, ciphertextAndTag);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts and verifies. Throws MessageIntegrityException if the authentication tag
     * does not match — this is the ONLY signal the caller should surface to the user
     * ("Unable to verify this message"); never expose the underlying exception detail.
     */
    public byte[] decrypt(byte[] nonce, byte[] ciphertextAndTag, SecretKey key)
            throws MessageIntegrityException {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(ciphertextAndTag);
        } catch (AEADBadTagException e) {
            throw new MessageIntegrityException("Authentication tag mismatch", e);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException
                | NoSuchAlgorithmException | NoSuchPaddingException
                | IllegalBlockSizeException | BadPaddingException e) {
            // Malformed input (wrong nonce length, corrupted extraction, etc.) is treated
            // the same as a tamper/integrity failure from the caller's point of view.
            throw new MessageIntegrityException("Payload could not be decrypted", e);
        }
    }

    /** Thrown when a message fails authentication — tampered, corrupted, or wrong key. */
    public static final class MessageIntegrityException extends Exception {
        public MessageIntegrityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
