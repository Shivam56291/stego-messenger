package com.stegomsg.db;

import com.stegomsg.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * All queries here use PreparedStatement with bound parameters — never string
 * concatenation — which is what actually prevents SQL injection (section 23).
 */
public final class UserRepository {

    private final Database database;

    public UserRepository(Database database) {
        this.database = database;
    }

    public void insert(User user) {
        String sql = """
            INSERT INTO users (id, email, display_alias, password_hash, public_key,
                                protected_private_key, pin_hash, pin_enabled,
                                failed_login_attempts, pin_failed_attempts, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getDisplayAlias());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getPublicKeyBase64());
            ps.setString(6, user.getProtectedPrivateKey());
            ps.setString(7, user.getPinHash());
            ps.setInt(8, user.isPinEnabled() ? 1 : 0);
            ps.setInt(9, user.getFailedLoginAttempts());
            ps.setInt(10, user.getPinFailedAttempts());
            ps.setString(11, user.getStatus());
            ps.setString(12, user.getCreatedAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw translateInsertError(e);
        }
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error looking up user by email", e);
        }
    }

    public Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error looking up user by id", e);
        }
    }

    public void updateSecurityState(User user) {
        String sql = """
            UPDATE users SET pin_hash = ?, pin_enabled = ?, failed_login_attempts = ?,
                              account_locked_until = ?, pin_failed_attempts = ?, pin_locked_until = ?,
                              status = ?
            WHERE id = ?
            """;
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getPinHash());
            ps.setInt(2, user.isPinEnabled() ? 1 : 0);
            ps.setInt(3, user.getFailedLoginAttempts());
            ps.setString(4, user.getAccountLockedUntil() == null ? null : user.getAccountLockedUntil().toString());
            ps.setInt(5, user.getPinFailedAttempts());
            ps.setString(6, user.getPinLockedUntil() == null ? null : user.getPinLockedUntil().toString());
            ps.setString(7, user.getStatus());
            ps.setString(8, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error updating user security state", e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_alias"),
                rs.getString("password_hash"),
                rs.getString("public_key"),
                rs.getString("protected_private_key"),
                Instant.parse(rs.getString("created_at"))
        );
        user.setPinHash(rs.getString("pin_hash"));
        user.setPinEnabled(rs.getInt("pin_enabled") == 1);
        user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        String lockedUntil = rs.getString("account_locked_until");
        if (lockedUntil != null) user.setAccountLockedUntil(Instant.parse(lockedUntil));
        user.setPinFailedAttempts(rs.getInt("pin_failed_attempts"));
        String pinLockedUntil = rs.getString("pin_locked_until");
        if (pinLockedUntil != null) user.setPinLockedUntil(Instant.parse(pinLockedUntil));
        user.setStatus(rs.getString("status"));
        return user;
    }

    /**
     * Translates a unique-constraint violation into a domain exception WITHOUT leaking
     * which specific constraint failed in a way that would let an attacker enumerate
     * registered emails — see ARCHITECTURE.md section 5 on privacy-conscious error
     * messages. The caller (AuthService) is responsible for showing a generic message.
     */
    private RuntimeException translateInsertError(SQLException e) {
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
            return new DuplicateEmailException();
        }
        return new IllegalStateException("Database error creating user", e);
    }

    public static final class DuplicateEmailException extends RuntimeException {
    }
}
