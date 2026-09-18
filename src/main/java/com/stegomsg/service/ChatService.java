package com.stegomsg.service;

import com.stegomsg.db.ConversationRepository;
import com.stegomsg.db.MessageRepository;
import com.stegomsg.db.UserRepository;
import com.stegomsg.model.Conversation;
import com.stegomsg.model.Message;
import com.stegomsg.model.User;
import com.stegomsg.util.Ids;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Conversation lookup/creation and authorization-checked message listing.
 *
 * Every read here goes through a query that also verifies conversation membership
 * (see MessageRepository/ConversationRepository) — a user can never list messages for
 * a conversation they are not part of, regardless of what conversationId they pass in.
 * This is the concrete implementation of the IDOR-prevention requirement in
 * ARCHITECTURE.md section 22.
 */
public final class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ChatService(ConversationRepository conversationRepository, MessageRepository messageRepository,
                        UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /** Looks up a user by contact email to start a new conversation (section 11). */
    public User findContactByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No user found with that email."));
    }

    public Conversation getOrCreateConversation(String selfUserId, String otherUserId) {
        if (selfUserId.equals(otherUserId)) {
            throw new IllegalArgumentException("You cannot start a conversation with yourself.");
        }
        return conversationRepository.findBetween(selfUserId, otherUserId)
                .orElseGet(() -> conversationRepository.insert(
                        new Conversation(Ids.newId(), selfUserId, otherUserId, Instant.now())));
    }

    public record ConversationSummary(Conversation conversation, User otherParticipant,
                                       Optional<Message> lastMessage, long unreadCount) {
    }

    public List<ConversationSummary> listConversationSummaries(String selfUserId) {
        List<Conversation> conversations = conversationRepository.findAllForUser(selfUserId);
        List<ConversationSummary> summaries = new ArrayList<>();
        for (Conversation c : conversations) {
            String otherId = c.otherParticipant(selfUserId);
            User other = userRepository.findById(otherId).orElse(null);
            if (other == null) continue; // defensive: participant deleted their account
            List<Message> messages = messageRepository.findByConversationForUser(c.getId(), selfUserId);
            Optional<Message> last = messages.stream().max(Comparator.comparing(Message::getCreatedAt));
            long unread = messages.stream()
                    .filter(m -> m.getRecipientId().equals(selfUserId) && m.isUnread())
                    .count();
            summaries.add(new ConversationSummary(c, other, last, unread));
        }
        summaries.sort((a, b) -> {
            Instant ta = a.lastMessage().map(Message::getCreatedAt).orElse(a.conversation().getCreatedAt());
            Instant tb = b.lastMessage().map(Message::getCreatedAt).orElse(b.conversation().getCreatedAt());
            return tb.compareTo(ta); // most recent first
        });
        return summaries;
    }

    public List<Message> listMessages(String conversationId, String requestingUserId) {
        return messageRepository.findByConversationForUser(conversationId, requestingUserId);
    }

    public void markRead(String messageId, String requestingUserId) {
        messageRepository.markRead(messageId, requestingUserId);
    }
}
