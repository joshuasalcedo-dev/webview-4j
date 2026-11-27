package io.joshuasalcedo.swaswa.demo;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.ui.chat.ChatEvent;
import io.joshuasalcedo.swaswa.ui.chat.ChatService;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPage;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPerson;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.Conversation;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.LastMessage;
import io.joshuasalcedo.swaswa.ui.chat.ChatTypes.OutgoingMessage;
import io.joshuasalcedo.swaswa.ui.chat.UiChatWindow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * swaswa-ui's chat, from Java: the window is swaswa-ui's (its look, the system's), the conversations and messages are
 * Java's ({@link ChatService}), and Ana answers what you send. Not deployed (swaswa-demo). {@code just chat-demo}.
 */
public class ChatDemo extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    private final UiChatWindow chat = new UiChatWindow();

    @Override
    protected WindowOptions window() {
        return WindowOptions.html("").size(1100, 760);
    }

    @Override
    protected void build() {
        setTitle("swaswa chat");
        chat.setAdapter(new Conversations().asAdapter());
        chat.setDefaultOpenId("ana");
        chat.setTitleBar(false);
        // the whole window (its style is the component's own); without its title bar, its one row is the body
        chat.getStyle().set(CssProperty.POSITION, "fixed").set(CssProperty.INSET, "0").set("grid-template-rows", "minmax(0, 1fr)");
        getMain().add(chat);
    }

    /** The service: two conversations in memory; Ana answers a second after you write. */
    static final class Conversations implements ChatService {

        private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();
        private final List<Consumer<ChatEvent>> windows = new CopyOnWriteArrayList<>();
        private int next = 100;

        Conversations() {
            messages.put("ana", new CopyOnWriteArrayList<>(List.of(
                    ChatMessage.text("a1", "ana", "Hi! Is the order ready?"),
                    ChatMessage.text("a2", "me", "Almost: it ships this afternoon."))));
            messages.put("team", new CopyOnWriteArrayList<>(List.of(ChatMessage.text("t1", "bo", "Standup in 5"))));
        }

        @Override
        public CompletableFuture<ChatPage<Conversation>> loadConversations(String cursor, String category) {
            return CompletableFuture.completedFuture(ChatPage.of(List.of(
                    conversation("ana", "Ana Reyes"),
                    conversation("team", "Team"))));
        }

        private Conversation conversation(String id, String title) {
            List<ChatMessage> list = messages.get(id);
            ChatMessage last = list.getLast();
            return new Conversation(id, title, new LastMessage(last.text(), last.senderId().equals("me"), last.sentAt()), 0);
        }

        @Override
        public CompletableFuture<ChatPage<ChatMessage>> loadMessages(String conversationId, String cursor) {
            return CompletableFuture.completedFuture(ChatPage.of(new ArrayList<>(messages.getOrDefault(conversationId, List.of()))));
        }

        @Override
        public CompletableFuture<ChatMessage> sendMessage(OutgoingMessage out) {
            ChatMessage stored = new ChatMessage("m" + next++, "me", null, out.text(), Instant.now(), "sent", null, out.replyTo(),
                    null, null, null, out.clientId());
            messages.computeIfAbsent(out.conversationId(), k -> new CopyOnWriteArrayList<>()).add(stored);
            if (out.conversationId().equals("ana")) answer(out.text());
            return CompletableFuture.completedFuture(stored);
        }

        /** Ana types, then answers. */
        private void answer(String what) {
            ChatPerson ana = new ChatPerson("ana", "Ana Reyes");
            windows.forEach(w -> w.accept(new ChatEvent.Typing("ana", ana, true)));
            CompletableFuture.delayedExecutor(1200, TimeUnit.MILLISECONDS).execute(() -> {
                ChatMessage reply = ChatMessage.text("m" + next++, "ana", "You said \"" + what + "\". Thanks!");
                messages.get("ana").add(reply);
                windows.forEach(w -> w.accept(new ChatEvent.MessageReceived("ana", reply)));
            });
        }

        @Override
        public void events(Consumer<ChatEvent> emit) {
            windows.add(emit);
        }
    }
}
