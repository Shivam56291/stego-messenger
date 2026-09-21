package com.stegomsg.service;

import com.stegomsg.db.SecurityEventRepository;
import com.stegomsg.db.UserRepository;
import com.stegomsg.model.User;
import com.stegomsg.security.CryptoService;
import com.stegomsg.security.DeviceKeyStore;
import com.stegomsg.security.KeyManager;
import com.stegomsg.security.PasswordHasher;
import com.stegomsg.util.Constants;
import com.stegomsg.util.Ids;
import com.stegomsg.util.Validation;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Optional;

/**
 * Registration, password login, and quick-PIN login — with the account-lockout and
 * PIN-lockout behaviour described in ARCHITECTURE.md sections 5/7/23.
 *
 * Design note on what each failure path returns: every user-facing failure uses the
 * {@link AuthException} hierarchy with intentionally generic messages (never "email not
 * found" vs "wrong password" as distinct signals) so a caller can't enumerate registered
 * emails through error-message differences — see section 5.
 */
public final class AuthService {

    private final UserRepository userRepository;
    private final SecurityEventRepository securityEvents;
    private final PasswordHasher passwordHasher;
    private final PasswordHasher pinHasher;
    private final KeyManager keyManager;
    private final CryptoService cryptoService;
    private final DeviceKeyStore deviceKeyStore;
    private final SessionService sessionService;

    public AuthService(UserRepository userRepository, SecurityEventRepository securityEvents,
                       PasswordHasher passwordHasher, PasswordHasher pinHasher, KeyManager keyManager,
                       CryptoService cryptoService, DeviceKeyStore deviceKeyStore, SessionService sessionService) {
        this.userRepository = userRepository;
        this.securityEvents = securityEvents;
        this.passwordHasher = passwordHasher;
        this.pinHasher = pinHasher;
        this.keyManager = keyManager;
        this.cryptoService = cryptoService;
        this.deviceKeyStore = deviceKeyStore;
        this.sessionService = sessionService;
    }

    public User register(String email, char[] password, String displayAlias) {
        if (!Validation.isValidEmail(email)) {
            throw new AuthException("Please enter a valid email address.");
        }
        Validation.PasswordStrength strength = Validation.assessPassword(new String(password));
        if (!strength.meetsMinimum()) {
            throw new AuthException(strength.feedback());
        }

        String passwordHash = passwordHasher.hash(password);
        KeyPair keyPair = keyManager.generateUserKeyPair();
        String publicKeyEncoded = keyManager.encodePublicKey(keyPair.getPublic());
        String protectedPrivateKey = keyManager.protectPrivateKey(keyPair.getPrivate(), password);

        User user = new User(Ids.newId(), email.trim().toLowerCase(), displayAlias, passwordHash,
                publicKeyEncoded, protectedPrivateKey, Instant.now());
        try {
            userRepository.insert(user);
        } catch (UserRepository.DuplicateEmailException e) {
            securityEvents.log(null, "REGISTRATION_REJECTED", "duplicate email attempt");
            throw new DuplicateAccountException("This email is already registered. Try signing in instead.");
        }
        securityEvents.log(user.getId(), "ACCOUNT_CREATED", null);
        return user;
    }

    public User login(String email, char[] password) {
        Optional<User> maybeUser = userRepository.findByEmail(email.trim().toLowerCase());
        // Constant messaging regardless of which check fails, to avoid user enumeration.
        AuthException genericFailure = new AuthException("Incorrect email or password.");

        if (maybeUser.isEmpty()) {
            securityEvents.log(null, "LOGIN_FAILURE", "unknown email");
            throw genericFailure;
        }
        User user = maybeUser.get();

        if (isLockedOut(user.getAccountLockedUntil())) {
            securityEvents.log(user.getId(), "LOGIN_BLOCKED", "account temporarily locked");
            throw new AuthException("Too many failed attempts. Please try again later.");
        }

        if (!passwordHasher.verify(password, user.getPasswordHash())) {
            registerFailedLogin(user);
            securityEvents.log(user.getId(), "LOGIN_FAILURE", "bad password");
            throw genericFailure;
        }

        PrivateKey privateKey;
        try {
            privateKey = keyManager.recoverPrivateKey(user.getProtectedPrivateKey(), password);
        } catch (CryptoService.MessageIntegrityException e) {
            // Should not normally happen if the password hash check above passed, but if the
            // protected-key record were somehow corrupted this is the safe generic failure.
            securityEvents.log(user.getId(), "LOGIN_FAILURE", "key recovery failed");
            throw genericFailure;
        }

        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        userRepository.updateSecurityState(user);
        securityEvents.log(user.getId(), "LOGIN_SUCCESS", null);

        sessionService.start(user, privateKey);
        return user;
    }

    /** Requires the user to already be logged in (re-authenticates nothing new — PIN is a convenience layer). */
    public void enableQuickPin(User user, char[] pin) {
        if (!Validation.isValidPin(new String(pin))) {
            throw new AuthException("PIN must be exactly 6 digits.");
        }
        PrivateKey privateKey = sessionService.requireCurrentPrivateKey();
        deviceKeyStore.store(user.getId(), privateKey, pin);
        user.setPinHash(pinHasher.hash(pin));
        user.setPinEnabled(true);
        user.setPinFailedAttempts(0);
        user.setPinLockedUntil(null);
        userRepository.updateSecurityState(user);
        securityEvents.log(user.getId(), "PIN_ENABLED", null);
    }

    public void disableQuickPin(User user) {
        deviceKeyStore.disable(user.getId());
        user.setPinHash(null);
        user.setPinEnabled(false);
        userRepository.updateSecurityState(user);
        securityEvents.log(user.getId(), "PIN_DISABLED", null);
    }

    /**
     * Quick login using the local device PIN. Only works on a device where the PIN was
     * previously enabled (DeviceKeyStore has a local record) — this is fundamentally a
     * per-device mechanism, not an alternative remote authentication method.
     */
    public User quickLogin(String email, char[] pin) {
        Optional<User> maybeUser = userRepository.findByEmail(email.trim().toLowerCase());
        AuthException genericFailure = new AuthException("Incorrect PIN.");
        if (maybeUser.isEmpty() || !maybeUser.get().isPinEnabled()) {
            throw genericFailure;
        }
        User user = maybeUser.get();

        if (isLockedOut(user.getPinLockedUntil())) {
            securityEvents.log(user.getId(), "PIN_LOGIN_BLOCKED", "PIN temporarily locked");
            throw new AuthException("Too many incorrect PIN attempts. Please try again later, or log in with your password.");
        }

        if (!pinHasher.verify(pin, user.getPinHash())) {
            registerFailedPin(user);
            securityEvents.log(user.getId(), "PIN_LOGIN_FAILURE", null);
            throw genericFailure;
        }

        try {
            byte[] rawPrivateKey = deviceKeyStore.recover(user.getId(), pin);
            PrivateKey privateKey = java.security.KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(rawPrivateKey));
            user.setPinFailedAttempts(0);
            user.setPinLockedUntil(null);
            userRepository.updateSecurityState(user);
            securityEvents.log(user.getId(), "PIN_LOGIN_SUCCESS", null);
            sessionService.start(user, privateKey);
            return user;
        } catch (Exception e) {
            registerFailedPin(user);
            securityEvents.log(user.getId(), "PIN_LOGIN_FAILURE", "device key recovery failed");
            throw genericFailure;
        }
    }

    public void logout() {
        User user = sessionService.isLoggedIn() ? sessionService.requireCurrentUser() : null;
        if (user != null) {
            securityEvents.log(user.getId(), "LOGOUT", null);
        }
        sessionService.endSession();
    }

    private void registerFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= Constants.MAX_LOGIN_ATTEMPTS_BEFORE_LOCKOUT) {
            // Progressive delay: lockout duration doubles per extra attempt beyond the threshold.
            long extra = attempts - Constants.MAX_LOGIN_ATTEMPTS_BEFORE_LOCKOUT;
            long seconds = Constants.LOGIN_LOCKOUT_SECONDS * (1L << Math.min(extra, 6));
            user.setAccountLockedUntil(Instant.now().plusSeconds(seconds));
        }
        userRepository.updateSecurityState(user);
    }

    private void registerFailedPin(User user) {
        int attempts = user.getPinFailedAttempts() + 1;
        user.setPinFailedAttempts(attempts);
        if (attempts >= Constants.MAX_PIN_ATTEMPTS_BEFORE_LOCKOUT) {
            long extra = attempts - Constants.MAX_PIN_ATTEMPTS_BEFORE_LOCKOUT;
            long seconds = Constants.PIN_LOCKOUT_SECONDS * (1L << Math.min(extra, 6));
            user.setPinLockedUntil(Instant.now().plusSeconds(seconds));
        }
        userRepository.updateSecurityState(user);
    }

    private boolean isLockedOut(Instant lockedUntil) {
        return lockedUntil != null && Instant.now().isBefore(lockedUntil);
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }

    /**
     * Explicit registration feedback for the desktop UX. This does reveal that an email
     * is registered, so it is intentionally limited to registration rather than login.
     */
    public static final class DuplicateAccountException extends AuthException {
        public DuplicateAccountException(String message) {
            super(message);
        }
    }
}
