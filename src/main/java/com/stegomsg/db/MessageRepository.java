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
            ps.setString(1, message.getId());
            ps.setString(2, message.getConversationId());
            ps.setString(3, message.getSenderId());
            ps.setString(4, message.getRecipientId());
            ps.setString(5, message.getImagePath());
            ps.setInt(6, message.getPayloadBytes());
            ps.setInt(7, message.getImageWidth());
            ps.setInt(8, message.getImageHeight());
            ps.setString(9, message.getStatus().name().toLowerCase());
            ps.setString(10, message.getCreatedAt().toString());
            ps.setString(11, message.getReadAt() == null ? null : message.getReadAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error saving message", e);
        }
    }

    /**
     * Fetches a message by id AND verifies the requester is the sender or recipient in
     * the same query — this is the IDOR guard from ARCHITECTURE.md section 22/33. If the
     * requester is not a party to the message, this returns empty exactly as if the
     * message didn't exist; the caller must never distinguish "not found" from
     * "not yours" in what it tells the UI.
     */
    public Optional<Message> findByIdForUser(String messageId, String requestingUserId) {
        String sql = "SELECT * FROM messages WHERE id = ? AND (sender_id = ? OR recipient_id = ?)";
        try (Connection conn = database.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, requestingUserId);
            ps.setString(3, requestingUserId);
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
            ps.setString(1, conversationId);
            ps.setString(2, requestingUserId);
            ps.setString(3, requestingUserId);
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
            ps.setString(1, Instant.now().toString());
            ps.setString(2, messageId);
            ps.setString(3, requestingUserId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Database error marking message read", e);
        }
    }

    private Message map(ResultSet rs) throws SQLException {
        String readAtStr = rs.getString("read_at");
        Message message = new Message(
                rs.getString("id"),
                rs.getString("conversation_id"),
                rs.getString("sender_id"),
                rs.getString("recipient_id"),
                rs.getString("image_path"),
                rs.getInt("payload_bytes"),
                rs.getInt("image_width"),
                rs.getInt("image_height"),
                Message.Status.valueOf(rs.getString("status").toUpperCase()),
                Instant.parse(rs.getString("created_at"))
        );
        if (readAtStr != null) {
            message.setReadAt(Instant.parse(readAtStr));
        }
        return message;
    }
}
