package com.stegomsg.security;

import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA512 password/PIN hasher.
 *
 * Encoded format (one line, '$'-delimited, self-describing so cost parameters
 * can change over time without breaking old hashes):
 *
 *   pbkdf2-sha512$&lt;iterations&gt;$&lt;base64(salt)&gt;$&lt;base64(derivedKey)&gt;
 *
 * Parameters follow current OWASP guidance for PBKDF2-HMAC-SHA512 (>= 210,000 iterations
 * as of the 2023 cheat sheet revision). A 16-byte (128-bit) random salt is generated per
 * secret via SecureRandomUtil — never reused, never derived from user data.
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA512";
    private static final String PREFIX = "pbkdf2-sha512";
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    private final int iterations;

    public Pbkdf2PasswordHasher() {
        this(210_000);
    }

    /** Package-visible constructor for tests that need fewer iterations for speed. */
    Pbkdf2PasswordHasher(int iterations) {
        if (iterations < 1000) {
            throw new IllegalArgumentException("Iteration count is dangerously low");
        }
        this.iterations = iterations;
    }

    @Override
    public String hash(char[] secret) {
        byte[] salt = SecureRandomUtil.randomBytes(SALT_LENGTH_BYTES);
        byte[] derived = pbkdf2(secret, salt, iterations);
        return PREFIX + "$" + iterations + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    @Override
    public boolean verify(char[] secret, String encodedHash) {
        if (encodedHash == null) {
            return false;
        }
        String[] parts = encodedHash.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iters = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(secret, salt, iters);
            return constantTimeEquals(expected, actual);
        } catch (RuntimeException e) {
            // Malformed hash (corrupted DB row, tampered value, etc.) -> treat as no match.
            return false;
        }
    }

    private byte[] pbkdf2(char[] secret, byte[] salt, int iters) {
        PBEKeySpec spec = new PBEKeySpec(secret, salt, iters, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (java.security.NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Constant-time comparison to avoid leaking hash equality via timing. */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
