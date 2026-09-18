package com.stegomsg.security;

/**
 * Abstraction over password/PIN hashing so the concrete algorithm can be swapped
 * without touching AuthService or the database layer.
 *
 * Shipped implementation: {@link Pbkdf2PasswordHasher} (PBKDF2-HMAC-SHA512), because it
 * needs zero third-party dependencies and is entirely standard-library, which matters in
 * constrained build environments and keeps the project buildable offline.
 *
 * For a production deployment, prefer Argon2id (memory-hard, resists GPU/ASIC cracking
 * far better than PBKDF2). Swap by writing an Argon2idPasswordHasher that implements this
 * interface using a library such as `de.mkammerer:argon2-jvm` or Bouncy Castle's Argon2
 * implementation, then change the single wiring point in AuthService's constructor.
 */
public interface PasswordHasher {

    /**
     * Hashes a password/PIN. Returns a single self-describing string containing the
     * algorithm identifier, cost parameters, salt and derived hash — everything needed
     * to verify later, nothing that leaks the secret.
     */
    String hash(char[] secret);

    /**
     * Verifies a plaintext secret against a previously produced encoded hash.
     * Constant-time comparison is used internally to avoid timing side-channels.
     */
    boolean verify(char[] secret, String encodedHash);
}
