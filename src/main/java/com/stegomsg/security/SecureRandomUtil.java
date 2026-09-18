package com.stegomsg.security;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Central source of cryptographically secure randomness for the application.
 * Every nonce, salt, session token and key uses this — never java.util.Random.
 */
public final class SecureRandomUtil {

    private static final SecureRandom SECURE_RANDOM = createStrong();

    private SecureRandomUtil() {
    }

    private static SecureRandom createStrong() {
        try {
            // Prefer a platform-strong instance (e.g. NativePRNGBlocking / Windows-PRNG).
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            // Fall back to the default SecureRandom, which is still CSPRNG-backed on all
            // major JVMs, just not guaranteed to be the platform's "strong" provider.
            return new SecureRandom();
        }
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static SecureRandom instance() {
        return SECURE_RANDOM;
    }
}
