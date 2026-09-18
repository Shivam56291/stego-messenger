package com.stegomsg.model;

import java.time.Instant;

/**
 * A single stego-message record. Note what is NOT here: plaintext content, the raw AES
 * key, the sender's private key. Only metadata plus a reference to the stego-image file
 * lives in this row — see ARCHITECTURE.md section Q for the full column rationale.
 */
public final class Message {
    public enum Status { SENDING, SENT, DELIVERED, FAILED }

    private final String id;
    private final String conversationId;
    private final String senderId;
    private final String recipientId;
    private final String imagePath;      // reference into object storage / local images dir
    private final int payloadBytes;      // actual embedded payload size, for history/UI
    private final int imageWidth;
    private final int imageHeight;
    private Status status;
    private final Instant createdAt;
    private Instant readAt;              // nullable

    public Message(String id, String conversationId, String senderId, String recipientId,
                    String imagePath, int payloadBytes, int imageWidth, int imageHeight,
                    Status status, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.imagePath = imagePath;
        this.payloadBytes = payloadBytes;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public String getRecipientId() { return recipientId; }
    public String getImagePath() { return imagePath; }
    public int getPayloadBytes() { return payloadBytes; }
    public int getImageWidth() { return imageWidth; }
    public int getImageHeight() { return imageHeight; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public boolean isUnread() { return readAt == null; }
}
