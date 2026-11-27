package io.joshuasalcedo.swaswa.ui.chat;

import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPerson;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.Conversation;
import java.time.Instant;

/**
 * What happened outside, as the service tells the window ({@link ChatService#events}): a message came, changed or
 * went, someone read, typed or came online, a conversation changed. Each says its {@code type}, as the window reads it.
 * <pre>{@code
 * emit.accept(new ChatEvent.MessageReceived("c1", ChatMessage.text("m9", "ana", "Hi!")));
 * }</pre>
 */
public sealed interface ChatEvent {

    String type();

    record MessageReceived(String type, String conversationId, ChatMessage message) implements ChatEvent {
        public MessageReceived(String conversationId, ChatMessage message) {
            this("message.received", conversationId, message);
        }
    }

    record MessageUpdated(String type, String conversationId, ChatMessage message) implements ChatEvent {
        public MessageUpdated(String conversationId, ChatMessage message) {
            this("message.updated", conversationId, message);
        }
    }

    record MessageDeleted(String type, String conversationId, String messageId) implements ChatEvent {
        public MessageDeleted(String conversationId, String messageId) {
            this("message.deleted", conversationId, messageId);
        }
    }

    /** Someone read up to this message. */
    record MessageRead(String type, String conversationId, String personId, String upToId) implements ChatEvent {
        public MessageRead(String conversationId, String personId, String upToId) {
            this("message.read", conversationId, personId, upToId);
        }
    }

    /** Someone is typing (or stopped); it ends by itself after a few seconds without another. */
    record Typing(String type, String conversationId, ChatPerson person, boolean typing) implements ChatEvent {
        public Typing(String conversationId, ChatPerson person, boolean typing) {
            this("typing", conversationId, person, typing);
        }
    }

    record Presence(String type, String personId, boolean online, Instant lastSeen) implements ChatEvent {
        public Presence(String personId, boolean online, Instant lastSeen) {
            this("presence", personId, online, lastSeen);
        }
    }

    /** A conversation changed (its title, its unread count, its last message...). */
    record ConversationUpdated(String type, Conversation conversation) implements ChatEvent {
        public ConversationUpdated(Conversation conversation) {
            this("conversation.updated", conversation);
        }
    }
}
