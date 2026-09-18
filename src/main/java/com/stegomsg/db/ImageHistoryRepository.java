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

/**
 * Implements the "last 10 images only" retention rule (ARCHITECTURE.md sections 19/38):
 * every insert is immediately followed by pruning anything beyond the newest N rows for
 * that user, so the table never accumulates more history than the product spec allows.
 */
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
        // SQLite has no simple "DELETE ... LIMIT with OFFSET" in one statement across all
        // builds, so pruning is done with a subquery selecting ids to keep.
        String pruneSql = """
            DELETE FROM image_history
            WHERE user_id = ? AND id NOT IN (
                SELECT id FROM image_history WHERE user_id = ? ORDER BY created_at DESC LIMIT ?
            )
            """;
        try (Connection conn = database.connect()) {
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, entry.getId());
                ps.setString(2, entry.getUserId());
                ps.setString(3, entry.getMessageId());
                ps.setString(4, entry.getImageName());
                ps.setInt(5, entry.getWidth());
                ps.setInt(6, entry.getHeight());
                ps.setLong(7, entry.getCapacityBytes());
                ps.setLong(8, entry.getPayloadBytes());
                ps.setString(9, entry.getRecipientAlias());
                ps.setString(10, entry.getCreatedAt().toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(pruneSql)) {
                ps.setString(1, entry.getUserId());
                ps.setString(2, entry.getUserId());
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
            ps.setString(1, userId);
            ps.setInt(2, Constants.MAX_IMAGE_HISTORY_ENTRIES);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ImageHistoryEntry(
                            rs.getString("id"),
                            rs.getString("user_id"),
                            rs.getString("message_id"),
                            rs.getString("image_name"),
                            rs.getInt("width"),
                            rs.getInt("height"),
                            rs.getLong("capacity_bytes"),
                            rs.getLong("payload_bytes"),
                            rs.getString("recipient_alias"),
                            Instant.parse(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error listing image history", e);
        }
        return results;
    }
}
