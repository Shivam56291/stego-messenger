package com.stegomsg.model;

import java.time.Instant;

/**
 * A user account. Deliberately minimal — see ARCHITECTURE.md section 25 for the
 * privacy rationale behind every field that IS here and every field that is NOT.
 *
 * Note what's absent: no real name, no phone number, no physical address, no profile
 * photo requirement. The only "identity" fields are the email (needed for account
 * recovery and contact discovery) and an optional self-chosen display alias (what
 * other users actually see in chat headers — see Conversation/Message UI).
 */
public final class User {
    private final String id; // UUID, never exposed in UI copy — see util/Ids
    private final String email;
    private String displayAlias;
    private final String passwordHash;      // PBKDF2 encoded string, see Pbkdf2PasswordHasher
    private final String publicKeyBase64;   // RSA public key, shareable
    private final String protectedPrivateKey; // encrypted-at-rest, see KeyManager
    private String pinHash;                 // nullable — only set if quick-login is enabled
    private boolean pinEnabled;
    private int failedLoginAttempts;
    private Instant accountLockedUntil;     // nullable
    private int pinFailedAttempts;
    private Instant pinLockedUntil;         // nullable
    private final Instant createdAt;
    private String status; // "active" | "locked" | "deleted"

    public User(String id, String email, String displayAlias, String passwordHash,
                String publicKeyBase64, String protectedPrivateKey, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.displayAlias = displayAlias;
        this.passwordHash = passwordHash;
        this.publicKeyBase64 = publicKeyBase64;
        this.protectedPrivateKey = protectedPrivateKey;
        this.createdAt = createdAt;
        this.status = "active";
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayAlias() { return displayAlias; }
    public void setDisplayAlias(String displayAlias) { this.displayAlias = displayAlias; }
    public String getPasswordHash() { return passwordHash; }
    public String getPublicKeyBase64() { return publicKeyBase64; }
    public String getProtectedPrivateKey() { return protectedPrivateKey; }
    public String getPinHash() { return pinHash; }
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }
    public boolean isPinEnabled() { return pinEnabled; }
    public void setPinEnabled(boolean pinEnabled) { this.pinEnabled = pinEnabled; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public Instant getAccountLockedUntil() { return accountLockedUntil; }
    public void setAccountLockedUntil(Instant accountLockedUntil) { this.accountLockedUntil = accountLockedUntil; }
    public int getPinFailedAttempts() { return pinFailedAttempts; }
    public void setPinFailedAttempts(int pinFailedAttempts) { this.pinFailedAttempts = pinFailedAttempts; }
    public Instant getPinLockedUntil() { return pinLockedUntil; }
    public void setPinLockedUntil(Instant pinLockedUntil) { this.pinLockedUntil = pinLockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** What the UI is allowed to show for "who is this" — never email, never raw id. */
    public String publicFacingLabel() {
        return (displayAlias != null && !displayAlias.isBlank()) ? displayAlias : "User " + id.substring(0, 8);
    }
}
