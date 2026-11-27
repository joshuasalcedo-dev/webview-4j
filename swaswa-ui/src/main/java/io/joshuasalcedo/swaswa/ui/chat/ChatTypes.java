package io.joshuasalcedo.swaswa.ui.chat;

import java.time.Instant;
import java.util.List;

/**
 * The chat's data, as Java gives it to {@link UiChatWindow} through a {@link ChatService} (and gets it back): people,
 * conversations, messages, pages of them, and what happened ({@link ChatEvent}). Each matches the chat's own type
 * field for field; a field left null is left out.
 */
public final class ChatTypes {

    private ChatTypes() {}

    /** Someone in a conversation. */
    public record ChatPerson(String id, String name, String avatar, Boolean online, Instant lastSeen) {
        public ChatPerson(String id, String name) {
            this(id, name, null, null, null);
        }
    }

    /** A reaction under a message: its emoji, how many, and whether one of them is mine. */
    public record Reaction(String emoji, int count, Boolean mine) {}

    /**
     * Something sent with a message. {@code kind}: "image" (url, width, height), "file" (name, size, url), "voice"
     * (duration, url), "tip" (amount in the currency's smallest unit, currency, note).
     */
    public record ChatAttachment(String kind, String url, String name, Double width, Double height, Double size, Double duration,
                                 Long amount, String currency, String note) {
        public static ChatAttachment image(String url) {
            return new ChatAttachment("image", url, null, null, null, null, null, null, null, null);
        }

        public static ChatAttachment file(String name, String url, double size) {
            return new ChatAttachment("file", url, name, null, null, size, null, null, null, null);
        }
    }

    /**
     * A message. {@code state}: "sending", "sent", "delivered", "read" or "failed"; {@code kind}: "message" or
     * "system". {@code clientId}: the id the window gave it while sending (so the stored one replaces it).
     */
    public record ChatMessage(String id, String senderId, String kind, String text, Instant sentAt, String state, Boolean edited,
                              String replyTo, List<Reaction> reactions, List<ChatAttachment> attachments, Boolean pinned, String clientId) {
        /** A message of text, sent now. */
        public static ChatMessage text(String id, String senderId, String text) {
            return new ChatMessage(id, senderId, null, text, Instant.now(), "sent", null, null, null, null, null, null);
        }
    }

    /** A conversation's last message, as its row shows it. */
    public record LastMessage(String text, Boolean fromMe, Instant at) {}

    /** A conversation, as the list and the header show it. */
    public record Conversation(String id, String title, String avatar, Boolean online, LastMessage lastMessage, Boolean typing,
                               Integer unread, Boolean muted, Boolean pinned, String draft, Boolean archived, Boolean group,
                               List<ChatPerson> people, String subtitle) {
        public Conversation(String id, String title, LastMessage lastMessage, Integer unread) {
            this(id, title, null, null, lastMessage, null, unread, null, null, null, null, null, null, null);
        }
    }

    /** A page of something, and where the next one starts (null: no more). */
    public record ChatPage<T>(List<T> items, String nextCursor) {
        public static <T> ChatPage<T> of(List<T> items) {
            return new ChatPage<>(items, null);
        }
    }

    /** What the window sends: {@code clientId} is the id it shows the message by while sending. */
    public record OutgoingMessage(String conversationId, String text, List<ChatAttachment> attachments, String replyTo, String clientId) {}

    /** What a search found. */
    public record ChatSearchResult(List<Conversation> conversations, List<ChatMessage> messages) {}
}
