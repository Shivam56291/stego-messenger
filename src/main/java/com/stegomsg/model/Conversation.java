package com.stegomsg.model;

import java.time.Instant;

/** A conversation between exactly two users (group chat is a documented future extension). */
public final class Conversation {
    private final String id;
    private final String userAId;
    private final String userBId;
    private final Instant createdAt;

    public Conversation(String id, String userAId, String userBId, Instant createdAt) {
        this.id = id;
        this.userAId = userAId;
        this.userBId = userBId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUserAId() { return userAId; }
    public String getUserBId() { return userBId; }
    public Instant getCreatedAt() { return createdAt; }

    public String otherParticipant(String selfUserId) {
        return selfUserId.equals(userAId) ? userBId : userAId;
    }

    public boolean hasParticipant(String userId) {
        return userAId.equals(userId) || userBId.equals(userId);
    }
}
