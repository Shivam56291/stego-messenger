package com.stegomsg.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Backs the "Quick PIN Login" feature (ARCHITECTURE.md section 7/J).
 *
 * WHAT THIS DOES vs WHAT IT DOES NOT DO — read this before assuming PIN == account
 * security:
 *   - This store holds a LOCAL, DEVICE-ONLY copy of the user's private key, wrapped
 *     with a key derived from their 6-digit PIN. It never touches the server/database.
 *   - Because it never leaves the device, a remote attacker who compromises the backend
 *     database gains nothing from it — they don't have the file. That is the actual
 *     security property a "local convenience" PIN provides.
 *   - It does NOT resist an attacker who has both (a) a copy of this local file and
 *     (b) unlimited offline guessing time: a 6-digit PIN is only ~20 bits of entropy
 *     (1,000,000 possibilities), and PBKDF2's cost factor cannot turn 20 bits into a
 *     safe keyspace against an offline attacker with real compute. The rate-limiting
 *     enforced by AuthService only helps against attempts made THROUGH the running
 *     application, not against someone who has exfiltrated this file directly.
 *   - For a real deployment, replace this file-based store with the OS-native secure
 *     storage (Windows DPAPI / macOS Keychain / a Linux Secret Service via a library
 *     such as java-keyring), which ties the secret to OS-level access control instead
 *     of file-system permissions alone.
 *
 * File format per user: base64(salt) '$' base64(nonce) '$' base64(ciphertext+tag)
 */
public final class DeviceKeyStore {

    private static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA512";
    private static final int PIN_KDF_ITERATIONS = 100_000; // see class javadoc: cost here mainly slows the legitimate device
    private static final int SALT_LENGTH = 16;

    private final Path storageDir;
    private final CryptoService cryptoService = new CryptoService();

    public DeviceKeyStore(Path storageDir) {
        this.storageDir = storageDir;
        try {
            Files.createDirectories(storageDir);
            restrictPermissions(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void store(String userId, PrivateKey privateKey, char[] pin) {
        byte[] salt = SecureRandomUtil.randomBytes(SALT_LENGTH);
        SecretKey wrappingKey = derive(pin, salt);
        CryptoService.EncryptedPayload encrypted = cryptoService.encrypt(privateKey.getEncoded(), wrappingKey);
        String record = Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(encrypted.nonce()) + "$"
                + Base64.getEncoder().encodeToString(encrypted.ciphertextAndTag());
        Path file = fileFor(userId);
        try {
            Files.writeString(file, record, StandardCharsets.UTF_8);
            restrictPermissions(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the raw PKCS8-encoded private key bytes, or throws if the PIN is wrong. */
    public byte[] recover(String userId, char[] pin) throws CryptoService.MessageIntegrityException {
        Path file = fileFor(userId);
        if (!Files.exists(file)) {
            throw new IllegalStateException("Quick login is not enabled on this device for this account");
        }
        try {
            String record = Files.readString(file, StandardCharsets.UTF_8);
            String[] parts = record.split("\\$");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            SecretKey wrappingKey = derive(pin, salt);
            return cryptoService.decrypt(nonce, ciphertext, wrappingKey);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isEnabledOnThisDevice(String userId) {
        return Files.exists(fileFor(userId));
    }

    public void disable(String userId) {
        try {
            Files.deleteIfExists(fileFor(userId));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path fileFor(String userId) {
        return storageDir.resolve(userId + ".devicekey");
    }

    private SecretKey derive(char[] pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, PIN_KDF_ITERATIONS, CryptoService.KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGO);
            return CryptoService.keyFromBytes(factory.generateSecret(spec).getEncoded());
        } catch (java.security.NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    private void restrictPermissions(Path path) throws IOException {
        try {
            Set<PosixFilePermission> ownerOnly = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, ownerOnly);
        } catch (UnsupportedOperationException e) {
            // Non-POSIX filesystem (e.g. Windows) — rely on OS default ACLs; a production
            // build should use DPAPI/Keychain there instead, as noted in the class javadoc.
        }
    }
}
