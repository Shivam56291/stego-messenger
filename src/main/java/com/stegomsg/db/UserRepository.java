package com.stegomsg.db;

import com.stegomsg.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** All user-account SQL is parameterized and PostgreSQL-aware. */
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
            Database.setUuid(ps, 1, user.getId());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getDisplayAlias());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getPublicKeyBase64());
            ps.setString(6, user.getProtectedPrivateKey());
            ps.setString(7, user.getPinHash());
            ps.setBoolean(8, user.isPinEnabled());
            ps.setInt(9, user.getFailedLoginAttempts());
            ps.setInt(10, user.getPinFailedAttempts());
            ps.setString(11, user.getStatus());
            Database.setInstant(ps, 12, user.getCreatedAt());
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
            Database.setUuid(ps, 1, id);
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
            ps.setBoolean(2, user.isPinEnabled());
            ps.setInt(3, user.getFailedLoginAttempts());
            Database.setInstant(ps, 4, user.getAccountLockedUntil());
            ps.setInt(5, user.getPinFailedAttempts());
            Database.setInstant(ps, 6, user.getPinLockedUntil());
            ps.setString(7, user.getStatus());
            Database.setUuid(ps, 8, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error updating user security state", e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User(
                Database.getUuid(rs, "id"),
                rs.getString("email"),
                rs.getString("display_alias"),
                rs.getString("password_hash"),
                rs.getString("public_key"),
                rs.getString("protected_private_key"),
                Database.getInstant(rs, "created_at")
        );
        user.setPinHash(rs.getString("pin_hash"));
        user.setPinEnabled(rs.getBoolean("pin_enabled"));
        user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        Instant lockedUntil = Database.getInstant(rs, "account_locked_until");
        if (lockedUntil != null) user.setAccountLockedUntil(lockedUntil);
        user.setPinFailedAttempts(rs.getInt("pin_failed_attempts"));
        Instant pinLockedUntil = Database.getInstant(rs, "pin_locked_until");
        if (pinLockedUntil != null) user.setPinLockedUntil(pinLockedUntil);
        user.setStatus(rs.getString("status"));
        return user;
    }

    private RuntimeException translateInsertError(SQLException e) {
        // PostgreSQL duplicate-key SQLSTATE is 23505. Keep the message generic at the
        // service/UI layer so registration cannot be used to enumerate existing emails.
        if ("23505".equals(e.getSQLState())
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique"))) {
            return new DuplicateEmailException();
        }
        return new IllegalStateException("Database error creating user", e);
    }

    public static final class DuplicateEmailException extends RuntimeException {
    }
}
