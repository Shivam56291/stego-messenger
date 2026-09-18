package com.stegomsg.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Implements the hybrid cryptosystem described in the architecture:
 *
 *   Each user has an RSA-2048 keypair.
 *     - Public key  -> stored server-side, freely shared, used by senders to wrap keys.
 *     - Private key -> NEVER stored in plaintext. It is encrypted at rest with a key
 *       derived from the user's own password (PBKDF2-HMAC-SHA512, distinct salt from
 *       the password-hash salt). It only exists in plaintext in memory, for the
 *       duration of an authenticated session, after the user has proven they know
 *       their password.
 *
 *   Per message:
 *     - A fresh random AES-256 key is generated (see CryptoService).
 *     - The message is encrypted with that key.
 *     - The AES key itself is "wrapped" (encrypted) using the RECIPIENT's RSA public
 *       key via RSA-OAEP. Only the recipient's private key can unwrap it.
 *
 * This means the server (and its database) never sees a usable AES key or plaintext
 * message key material — it only ever stores/relays opaque wrapped blobs.
 */
public final class KeyManager {

    private static final String RSA_ALGORITHM = "RSA";
    private static final int RSA_KEY_SIZE = 2048;
    private static final String WRAP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private static final String PRIVATE_KEY_WRAP_ALGO = "PBKDF2WithHmacSHA512";
    private static final int PRIVATE_KEY_KDF_ITERATIONS = 210_000;
    private static final int PRIVATE_KEY_SALT_LEN = 16;

    public KeyPair generateUserKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(RSA_KEY_SIZE, SecureRandomUtil.instance());
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA unavailable on this JVM", e);
        }
    }

    public String encodePublicKey(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public PublicKey decodePublicKey(String base64) {
        try {
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
            return kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Stored public key is invalid", e);
        }
    }

    /**
     * Encrypts a private key for storage, using a key derived from the owner's password.
     * Encoded format: base64(salt) '$' base64(nonce) '$' base64(ciphertext+tag)
     */
    public String protectPrivateKey(PrivateKey privateKey, char[] ownerPassword) {
        byte[] salt = SecureRandomUtil.randomBytes(PRIVATE_KEY_SALT_LEN);
        SecretKey wrappingKey = deriveWrappingKey(ownerPassword, salt);
        CryptoService crypto = new CryptoService();
        CryptoService.EncryptedPayload encrypted = crypto.encrypt(privateKey.getEncoded(), wrappingKey);
        return Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(encrypted.nonce()) + "$"
                + Base64.getEncoder().encodeToString(encrypted.ciphertextAndTag());
    }

    /**
     * Recovers a private key using the owner's password. Throws if the password is wrong
     * (the AES-GCM tag check inside CryptoService will fail) or the record is corrupted.
     */
    public PrivateKey recoverPrivateKey(String protectedRecord, char[] ownerPassword)
            throws CryptoService.MessageIntegrityException {
        String[] parts = protectedRecord.split("\\$");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed protected private key record");
        }
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] nonce = Base64.getDecoder().decode(parts[1]);
        byte[] ciphertextAndTag = Base64.getDecoder().decode(parts[2]);
        SecretKey wrappingKey = deriveWrappingKey(ownerPassword, salt);
        CryptoService crypto = new CryptoService();
        byte[] rawPrivateKey = crypto.decrypt(nonce, ciphertextAndTag, wrappingKey);
        try {
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
            return kf.generatePrivate(new PKCS8EncodedKeySpec(rawPrivateKey));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Recovered key material is not a valid RSA key", e);
        }
    }

    /** RSA-OAEP-wraps an AES message key for a specific recipient. */
    public byte[] wrapMessageKey(SecretKey aesKey, PublicKey recipientPublicKey) {
        try {
            Cipher cipher = Cipher.getInstance(WRAP_TRANSFORMATION);
            cipher.init(Cipher.WRAP_MODE, recipientPublicKey, OAEP_SPEC, SecureRandomUtil.instance());
            return cipher.wrap(aesKey);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Key wrapping failed", e);
        }
    }

    /** Unwraps an AES message key using the recipient's own private key. */
    public SecretKey unwrapMessageKey(byte[] wrappedKey, PrivateKey recipientPrivateKey) {
        try {
            Cipher cipher = Cipher.getInstance(WRAP_TRANSFORMATION);
            cipher.init(Cipher.UNWRAP_MODE, recipientPrivateKey, OAEP_SPEC);
            return (SecretKey) cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
        } catch (GeneralSecurityException e) {
            // Wrong key, wrong recipient, or corrupted blob all land here — surfaced to
            // the caller uniformly as an integrity/authorization problem, not detailed.
            throw new IllegalStateException("Key unwrapping failed", e);
        }
    }

    private SecretKey deriveWrappingKey(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PRIVATE_KEY_KDF_ITERATIONS, CryptoService.KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PRIVATE_KEY_WRAP_ALGO);
            byte[] derived = factory.generateSecret(spec).getEncoded();
            return CryptoService.keyFromBytes(derived);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Key derivation unavailable on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }
}
