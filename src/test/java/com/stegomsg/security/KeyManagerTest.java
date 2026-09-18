package com.stegomsg.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;

import static org.junit.jupiter.api.Assertions.*;

class KeyManagerTest {

    private final KeyManager keyManager = new KeyManager();
    private final CryptoService crypto = new CryptoService();

    @Test
    void wrappedKeyCanOnlyBeUnwrappedByTheIntendedRecipient() {
        KeyPair alice = keyManager.generateUserKeyPair();
        KeyPair bob = keyManager.generateUserKeyPair();
        SecretKey aesKey = crypto.generateMessageKey();

        byte[] wrappedForAlice = keyManager.wrapMessageKey(aesKey, alice.getPublic());

        // Alice can unwrap her own key...
        SecretKey recovered = keyManager.unwrapMessageKey(wrappedForAlice, alice.getPrivate());
        assertArrayEquals(aesKey.getEncoded(), recovered.getEncoded());

        // ...but Bob cannot, even though he has a valid RSA private key of his own.
        assertThrows(IllegalStateException.class,
                () -> keyManager.unwrapMessageKey(wrappedForAlice, bob.getPrivate()));
    }

    @Test
    void privateKeyIsRecoverableWithCorrectPasswordOnly() throws Exception {
        KeyPair pair = keyManager.generateUserKeyPair();
        char[] password = "correct horse battery staple".toCharArray();

        String protectedRecord = keyManager.protectPrivateKey(pair.getPrivate(), password);
        PrivateKey recovered = keyManager.recoverPrivateKey(protectedRecord, password);

        assertArrayEquals(pair.getPrivate().getEncoded(), recovered.getEncoded());
    }

    @Test
    void privateKeyRecoveryFailsWithWrongPassword() throws Exception {
        KeyPair pair = keyManager.generateUserKeyPair();
        String protectedRecord = keyManager.protectPrivateKey(pair.getPrivate(), "correct password".toCharArray());

        assertThrows(CryptoService.MessageIntegrityException.class,
                () -> keyManager.recoverPrivateKey(protectedRecord, "wrong password".toCharArray()));
    }

    @Test
    void protectedPrivateKeyRecordNeverContainsRawKeyBytes() {
        KeyPair pair = keyManager.generateUserKeyPair();
        String protectedRecord = keyManager.protectPrivateKey(pair.getPrivate(), "password123456".toCharArray());
        String rawKeyBase64 = java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        assertFalse(protectedRecord.contains(rawKeyBase64), "Encrypted record must not leak the raw key material");
    }
}
