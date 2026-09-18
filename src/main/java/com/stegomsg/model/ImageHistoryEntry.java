package com.stegomsg.model;

import java.time.Instant;

/** One row of a user's last-10 image usage history (see ARCHITECTURE.md section 19/38). */
public final class ImageHistoryEntry {
    private final String id;
    private final String userId;
    private final String messageId;
    private final String imageName;
    private final int width;
    private final int height;
    private final long capacityBytes;
    private final long payloadBytes;
    private final String recipientAlias;
    private final Instant createdAt;

    public ImageHistoryEntry(String id, String userId, String messageId, String imageName,
                              int width, int height, long capacityBytes, long payloadBytes,
                              String recipientAlias, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.messageId = messageId;
        this.imageName = imageName;
        this.width = width;
        this.height = height;
        this.capacityBytes = capacityBytes;
        this.payloadBytes = payloadBytes;
        this.recipientAlias = recipientAlias;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getMessageId() { return messageId; }
    public String getImageName() { return imageName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getCapacityBytes() { return capacityBytes; }
    public long getPayloadBytes() { return payloadBytes; }
    public String getRecipientAlias() { return recipientAlias; }
    public Instant getCreatedAt() { return createdAt; }
}
