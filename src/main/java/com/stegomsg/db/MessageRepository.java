package com.stegomsg.db;

import com.stegomsg.model.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MessageRepository {

    private final Database database;

    public MessageRepository(Database database) {
        this.database = database;
    }

    public void insert(Message message) {
        String sql = """
            INSERT INTO messages (id, conversation_id, sender_id, recipient_id, image_path,
                                   payload_bytes, image_width, image_height, status, created_at, read_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, message.getId());
            Database.setUuid(ps, 2, message.getConversationId());
            Database.setUuid(ps, 3, message.getSenderId());
            Database.setUuid(ps, 4, message.getRecipientId());
            ps.setString(5, message.getImagePath());
            ps.setInt(6, message.getPayloadBytes());
            ps.setInt(7, message.getImageWidth());
            ps.setInt(8, message.getImageHeight());
            ps.setString(9, message.getStatus().name().toLowerCase());
            Database.setInstant(ps, 10, message.getCreatedAt());
            Database.setInstant(ps, 11, message.getReadAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error saving message", e);
        }
    }

    /** Fetches a message only when the requester is sender or recipient. */
    public Optional<Message> findByIdForUser(String messageId, String requestingUserId) {
        String sql = "SELECT * FROM messages WHERE id = ? AND (sender_id = ? OR recipient_id = ?)";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, messageId);
            Database.setUuid(ps, 2, requestingUserId);
            Database.setUuid(ps, 3, requestingUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error looking up message", e);
        }
    }

    public List<Message> findByConversationForUser(String conversationId, String requestingUserId) {
        // The conversation_id + membership check together enforce that only participants
        // can ever see this thread's messages.
        String sql = """
            SELECT m.* FROM messages m
            JOIN conversations c ON c.id = m.conversation_id
            WHERE m.conversation_id = ? AND (c.user_a_id = ? OR c.user_b_id = ?)
            ORDER BY m.created_at ASC
            """;
        List<Message> results = new ArrayList<>();
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setUuid(ps, 1, conversationId);
            Database.setUuid(ps, 2, requestingUserId);
            Database.setUuid(ps, 3, requestingUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error listing messages", e);
        }
        return results;
    }

    public void markRead(String messageId, String requestingUserId) {
        String sql = "UPDATE messages SET read_at = ? WHERE id = ? AND recipient_id = ?";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.setInstant(ps, 1, Instant.now());
            Database.setUuid(ps, 2, messageId);
            Database.setUuid(ps, 3, requestingUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error marking message read", e);
        }
    }

    private Message map(ResultSet rs) throws SQLException {
        Message message = new Message(
                Database.getUuid(rs, "id"),
                Database.getUuid(rs, "conversation_id"),
                Database.getUuid(rs, "sender_id"),
                Database.getUuid(rs, "recipient_id"),
                rs.getString("image_path"),
                rs.getInt("payload_bytes"),
                rs.getInt("image_width"),
                rs.getInt("image_height"),
                Message.Status.valueOf(rs.getString("status").toUpperCase()),
                Database.getInstant(rs, "created_at")
        );
        message.setReadAt(Database.getInstant(rs, "read_at"));
        return message;
    }
}
