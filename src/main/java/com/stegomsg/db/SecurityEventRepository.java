package com.stegomsg.db;

import com.stegomsg.util.Ids;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Append-only security/audit log (ARCHITECTURE.md section 36).
 * NEVER pass a password, PIN, private key, session token, or message plaintext into
 * {@code detail} — this class does not sanitize; callers are responsible for only
 * logging non-sensitive event context (e.g. "login failed: bad password" is fine,
 * the password itself is not).
 */
public final class SecurityEventRepository {

    private final Database database;

    public SecurityEventRepository(Database database) {
        this.database = database;
    }

    public void log(String userIdOrNull, String eventType, String nonSensitiveDetail) {
        String sql = "INSERT INTO security_events (id, user_id, event_type, detail, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Ids.newId());
            ps.setString(2, userIdOrNull);
            ps.setString(3, eventType);
            ps.setString(4, nonSensitiveDetail);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Logging failures must never break the primary operation (login, send, etc.)
            System.err.println("[security-log] failed to record event " + eventType + ": " + e.getMessage());
        }
    }
}
