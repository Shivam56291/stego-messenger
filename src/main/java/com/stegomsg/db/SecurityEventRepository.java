package com.stegomsg.db;

import com.stegomsg.util.Ids;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

/** Append-only security/audit log. Never pass secrets into {@code detail}. */
public final class SecurityEventRepository {

    private final Database database;

    public SecurityEventRepository(Database database) {
        this.database = database;
    }

    public void log(String userIdOrNull, String eventType, String nonSensitiveDetail) {
        String sql = "INSERT INTO security_events (id, user_id, event_type, detail, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, Ids.newId());
            Database.setUuid(ps, 2, userIdOrNull);
            ps.setString(3, eventType);
            ps.setString(4, nonSensitiveDetail);
            Database.setInstant(ps, 5, Instant.now());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Audit logging remains non-blocking for the primary operation.
            System.err.println("[security-log] failed to record event " + eventType + ": " + e.getMessage());
        }
    }
}
