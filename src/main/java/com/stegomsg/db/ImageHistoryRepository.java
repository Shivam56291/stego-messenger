package com.stegomsg.db;

import com.stegomsg.model.ImageHistoryEntry;
import com.stegomsg.util.Constants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persists and prunes a user's last-10 image usage history. */
public final class ImageHistoryRepository {

    private final Database database;

    public ImageHistoryRepository(Database database) {
        this.database = database;
    }

    public void insertAndPrune(ImageHistoryEntry entry) {
        String insertSql = """
            INSERT INTO image_history (id, user_id, message_id, image_name, width, height,
                                        capacity_bytes, payload_bytes, recipient_alias, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String pruneSql = """
            DELETE FROM image_history
            WHERE user_id = ? AND id NOT IN (
                SELECT id FROM image_history WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
            )
            """;
        try (Connection conn = database.connect()) {
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                Database.setUuid(ps, 1, entry.getId());
                Database.setUuid(ps, 2, entry.getUserId());
                Database.setUuid(ps, 3, entry.getMessageId());
                ps.setString(4, entry.getImageName());
                ps.setInt(5, entry.getWidth());
                ps.setInt(6, entry.getHeight());
                ps.setLong(7, entry.getCapacityBytes());
                ps.setLong(8, entry.getPayloadBytes());
                ps.setString(9, entry.getRecipientAlias());
                Database.setInstant(ps, 10, entry.getCreatedAt());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(pruneSql)) {
                Database.setUuid(ps, 1, entry.getUserId());
                Database.setUuid(ps, 2, entry.getUserId());
                ps.setInt(3, Constants.MAX_IMAGE_HISTORY_ENTRIES);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error recording image history", e);
        }
    }

    public List<ImageHistoryEntry> findForUser(String userId) {
        String sql = "SELECT * FROM image_history WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<ImageHistoryEntry> results = new ArrayList<>();
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, userId);
            ps.setInt(2, Constants.MAX_IMAGE_HISTORY_ENTRIES);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ImageHistoryEntry(
                            Database.getUuid(rs, "id"),
                            Database.getUuid(rs, "user_id"),
                            Database.getUuid(rs, "message_id"),
                            rs.getString("image_name"),
                            rs.getInt("width"),
                            rs.getInt("height"),
                            rs.getLong("capacity_bytes"),
                            rs.getLong("payload_bytes"),
                            rs.getString("recipient_alias"),
                            Database.getInstant(rs, "created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error listing image history", e);
        }
        return results;
    }
}
