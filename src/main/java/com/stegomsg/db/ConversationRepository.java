package com.stegomsg.db;

import com.stegomsg.model.Conversation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConversationRepository {

    private final Database database;

    public ConversationRepository(Database database) {
        this.database = database;
    }

    /** Finds the existing conversation between two users, if any (order-independent). */
    public Optional<Conversation> findBetween(String userIdA, String userIdB) {
        String sql = """
            SELECT * FROM conversations
            WHERE (user_a_id = ? AND user_b_id = ?) OR (user_a_id = ? AND user_b_id = ?)
            """;
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, userIdA);
            Database.setUuid(ps, 2, userIdB);
            Database.setUuid(ps, 3, userIdB);
            Database.setUuid(ps, 4, userIdA);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error looking up conversation", e);
        }
    }

    public Conversation insert(Conversation conversation) {
        String sql = "INSERT INTO conversations (id, user_a_id, user_b_id, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, conversation.getId());
            Database.setUuid(ps, 2, conversation.getUserAId());
            Database.setUuid(ps, 3, conversation.getUserBId());
            Database.setInstant(ps, 4, conversation.getCreatedAt());
            ps.executeUpdate();
            return conversation;
        } catch (SQLException e) {
            throw new IllegalStateException("Database error creating conversation", e);
        }
    }

    /** Every conversation a given user actually belongs to — this IS the authorization boundary. */
    public List<Conversation> findAllForUser(String userId) {
        String sql = "SELECT * FROM conversations WHERE user_a_id = ? OR user_b_id = ? ORDER BY created_at DESC";
        List<Conversation> results = new ArrayList<>();
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, userId);
            Database.setUuid(ps, 2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error listing conversations", e);
        }
        return results;
    }

    public Optional<Conversation> findById(String id) {
        String sql = "SELECT * FROM conversations WHERE id = ?";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error looking up conversation", e);
        }
    }

    private Conversation map(ResultSet rs) throws SQLException {
        return new Conversation(
                Database.getUuid(rs, "id"),
                Database.getUuid(rs, "user_a_id"),
                Database.getUuid(rs, "user_b_id"),
                Database.getInstant(rs, "created_at")
        );
    }
}
