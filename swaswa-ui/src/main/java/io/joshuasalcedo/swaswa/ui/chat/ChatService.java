package io.joshuasalcedo.swaswa.ui.chat;

import io.joshuasalcedo.swaswa.react.ReactAdapter;
import io.joshuasalcedo.swaswa.react.ReactCall;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPage;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatSearchResult;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.Conversation;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.OutgoingMessage;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The service a chat window talks to, in Java: what it reads (conversations, messages), what it sends, and what
 * happened outside ({@link #events}). Every answer is a future: the window waits for it, and shows a failure's message
 * ("You're offline."). Three are needed; the others answer "Not available." until implemented (reading and typing
 * just say done).
 * <pre>{@code
 * UiChatWindow chat = new UiChatWindow();
 * chat.setAdapter(new MyChat().asAdapter());     // MyChat implements ChatService
 * }</pre>
 */
public interface ChatService {

    /** Conversations, newest activity first ({@code category}: "all", "unread", the app's own; may be null). */
    CompletableFuture<ChatPage<Conversation>> loadConversations(String cursor, String category);

    /** Messages before {@code cursor}, newest last (null cursor: the newest page). */
    CompletableFuture<ChatPage<ChatMessage>> loadMessages(String conversationId, String cursor);

    /** Sends it: the message as stored (its real id, time, state). */
    CompletableFuture<ChatMessage> sendMessage(OutgoingMessage message);

    /**
     * What happens outside, told to the window from now on (any thread): a message received, someone typing... Called
     * once per window that listens.
     */
    default void events(Consumer<ChatEvent> emit) {}

    default CompletableFuture<ChatMessage> editMessage(String conversationId, String messageId, String text) {
        return notAvailable();
    }

    /** Takes one's own message back for everyone. */
    default CompletableFuture<Void> unsendMessage(String conversationId, String messageId) {
        return notAvailable();
    }

    default CompletableFuture<Void> deleteMessage(String conversationId, String messageId) {
        return notAvailable();
    }

    default CompletableFuture<Void> react(String conversationId, String messageId, String reactionId) {
        return notAvailable();
    }

    default CompletableFuture<Void> unreact(String conversationId, String messageId, String reactionId) {
        return notAvailable();
    }

    default CompletableFuture<Void> forward(String messageId, List<String> toConversationIds) {
        return notAvailable();
    }

    default CompletableFuture<Void> pin(String conversationId, String messageId, boolean pinned) {
        return notAvailable();
    }

    /** Everything up to (and with) this message has been seen. */
    default CompletableFuture<Void> markRead(String conversationId, String upToId) {
        return CompletableFuture.completedFuture(null);
    }

    /** I started or stopped typing there. */
    default CompletableFuture<Void> setTyping(String conversationId, boolean typing) {
        return CompletableFuture.completedFuture(null);
    }

    /** Muted until then (null: unmuted). */
    default CompletableFuture<Void> mute(String conversationId, Instant until) {
        return notAvailable();
    }

    default CompletableFuture<Void> block(String personId, boolean blocked) {
        return notAvailable();
    }

    default CompletableFuture<ChatSearchResult> search(String query) {
        return CompletableFuture.completedFuture(new ChatSearchResult(List.of(), List.of()));
    }

    /** "Not available.": what a function the service does not have answers. */
    static <T> CompletableFuture<T> notAvailable() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Not available."));
    }

    /** This service as the chat window's adapter: each of the window's calls to the method it is. */
    default ReactAdapter asAdapter() {
        ChatService service = this;
        return new ReactAdapter() {
            @Override
            public CompletableFuture<?> call(ReactCall c) {
                return switch (c.method()) {
                    case "queries.loadConversations" -> service.loadConversations(c.arg(0, String.class), c.arg(1, String.class));
                    case "queries.loadMessages" -> service.loadMessages(c.arg(0, String.class), c.arg(1, String.class));
                    case "queries.search" -> service.search(c.arg(0, String.class));
                    case "commands.sendMessage" -> service.sendMessage(c.arg(0, OutgoingMessage.class));
                    case "commands.editMessage" -> service.editMessage(c.arg(0, String.class), c.arg(1, String.class), c.arg(2, String.class));
                    case "commands.unsendMessage" -> service.unsendMessage(c.arg(0, String.class), c.arg(1, String.class));
                    case "commands.deleteMessage" -> service.deleteMessage(c.arg(0, String.class), c.arg(1, String.class));
                    case "commands.react" -> service.react(c.arg(0, String.class), c.arg(1, String.class), c.arg(2, String.class));
                    case "commands.unreact" -> service.unreact(c.arg(0, String.class), c.arg(1, String.class), c.arg(2, String.class));
                    case "commands.forward" -> service.forward(c.arg(0, String.class), forwardTo(c));
                    case "commands.pin" -> service.pin(c.arg(0, String.class), c.arg(1, String.class), Boolean.TRUE.equals(c.arg(2, Boolean.class)));
                    case "commands.markRead" -> service.markRead(c.arg(0, String.class), c.arg(1, String.class));
                    case "commands.setTyping" -> service.setTyping(c.arg(0, String.class), Boolean.TRUE.equals(c.arg(1, Boolean.class)));
                    case "commands.mute" -> service.mute(c.arg(0, String.class), c.arg(1, Instant.class));
                    case "commands.block" -> service.block(c.arg(0, String.class), Boolean.TRUE.equals(c.arg(1, Boolean.class)));
                    default -> notAvailable();
                };
            }

            @Override
            public void subscribe(String method, ReactEmitter emitter) {
                if (method.equals("events.subscribe")) service.events(emitter::emit);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> forwardTo(ReactCall c) {
        List<String> to = c.arg(1, List.class);
        return to == null ? List.of() : to;
    }
}
