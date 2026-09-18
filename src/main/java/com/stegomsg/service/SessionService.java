package com.stegomsg.service;

import com.stegomsg.model.User;

import java.security.PrivateKey;

/**
 * Holds the current authenticated session's in-memory state for this desktop process.
 *
 * Deliberately NOT persisted anywhere: the decrypted private key lives here in RAM only
 * for the duration of the logged-in session, and is discarded on logout/app exit. This
 * is what ARCHITECTURE.md section 34 (offline/local security) means by "keys should not
 * sit around decrypted longer than necessary."
 */
public final class SessionService {

    private User currentUser;
    private PrivateKey currentPrivateKey; // decrypted for this session only — never written to disk

    public void start(User user, PrivateKey privateKey) {
        this.currentUser = user;
        this.currentPrivateKey = privateKey;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public User requireCurrentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("No user is currently logged in");
        }
        return currentUser;
    }

    public PrivateKey requireCurrentPrivateKey() {
        if (currentPrivateKey == null) {
            throw new IllegalStateException("No private key available for the current session");
        }
        return currentPrivateKey;
    }

    /** Clears all in-memory session state, including the decrypted key material. */
    public void endSession() {
        currentUser = null;
        currentPrivateKey = null; // no secure-wipe of the key object itself — a documented
        // limitation of using java.security.PrivateKey, which the JCA does not expose a
        // reliable zeroing API for; see ARCHITECTURE.md's offline-security notes.
    }
}
