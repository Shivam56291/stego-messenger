package com.stegomsg.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Pbkdf2PasswordHasherTest {

    private final PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void verifiesCorrectPassword() {
        String hash = hasher.hash("correct horse battery staple".toCharArray());
        assertTrue(hasher.verify("correct horse battery staple".toCharArray(), hash));
    }

    @Test
    void rejectsWrongPassword() {
        String hash = hasher.hash("correct horse battery staple".toCharArray());
        assertFalse(hasher.verify("wrong password".toCharArray(), hash));
    }

    @Test
    void neverStoresPlaintext() {
        String hash = hasher.hash("correct horse battery staple".toCharArray());
        assertFalse(hash.contains("correct horse"));
    }

    @Test
    void twoHashesOfTheSamePasswordDiffer() {
        // Different random salts each time -> different encoded output, even for the same input.
        String hashA = hasher.hash("same password".toCharArray());
        String hashB = hasher.hash("same password".toCharArray());
        assertNotEquals(hashA, hashB);
        assertTrue(hasher.verify("same password".toCharArray(), hashA));
        assertTrue(hasher.verify("same password".toCharArray(), hashB));
    }

    @Test
    void rejectsMalformedHash() {
        assertFalse(hasher.verify("anything".toCharArray(), "not-a-valid-hash"));
        assertFalse(hasher.verify("anything".toCharArray(), null));
    }
}
