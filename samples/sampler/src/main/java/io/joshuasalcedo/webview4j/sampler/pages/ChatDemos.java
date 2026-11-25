package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Sampler's pages: the chat's parts (conversations, header, messages, voice note, composer) and the empty, paged
 * and loading states. Their Java classes are made from the React components' props (java-gen).
 */
final class ChatDemos implements Demos {

    /** A message as the chat's types make it (generated from its TypeScript): who, when, what. */
    private static ChatMessage message(String id, String from, String text, String at) {
        return new ChatMessage(id, from, at).withText(text);
    }

    private static Conversation conversation(String id, String title, String last, String at, double unread) {
        return new Conversation(id, title).withLastMessage(new Conversation.LastMessage(at).withText(last)).withUnread(unread);
    }

    private final List<ChatMessage> messages = new ArrayList<>(List.of(
            message("m1", "ana", "Morning! Is the release still on for today?", "2026-11-25T08:58:00Z"),
            message("m2", "me", "Yes, after the tests are green.", "2026-11-25T09:01:00Z"),
            message("m3", "ana", "Great, ping me when it's out.", "2026-11-25T09:02:00Z")));
    private String selected = "ana";
    private String said = "Pick a conversation, write a message and press Enter.";
    private double page = 1;
    private boolean empty = true;

    private static final Map<String, ChatPerson> PEOPLE = Map.of(
            "ana", new ChatPerson("ana", "Ana Reyes").withOnline(true),
            "me", new ChatPerson("me", "You").withOnline(true));

    private static final List<Conversation> CONVERSATIONS = List.of(
            conversation("ana", "Ana Reyes", "Great, ping me when it's out.", "2026-11-25T09:02:00Z", 1)
                    .withTags(List.of(new ChatTag("team", "Team").withColor(PillColor.BLUE))),
            conversation("marco", "Marco Diaz", "See you at 3", "2026-11-24T15:20:00Z", 0),
            conversation("ops", "Ops on call", "All quiet tonight", "2026-11-24T22:10:00Z", 4));

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Chat", "Conversation", () -> row(
                        new ConversationList(CONVERSATIONS).selectedId(selected).onSelect(id -> {
                            selected = id;
                            said = "Opened " + id;
                        }).width(280),
                        column(
                                new ChatHeader(title(selected)).online(selected.equals("ana")).onInfo(() -> said = "Details of " + selected),
                                new MessageList(selected.equals("ana") ? messages : List.of()).me("me").people(PEOPLE).grow()
                                        .emptyText("No messages here yet"),
                                new ChatComposer().placeholder("Message " + title(selected)).onSend(v -> {
                                    Object text = v instanceof Map<?, ?> m ? m.get("text") : null;
                                    if (text instanceof String t && !t.isBlank()) {
                                        messages.add(message("m" + (messages.size() + 1), "me", t, java.time.Instant.now().toString()));
                                        said = "Sent: " + t;
                                    }
                                }),
                                text(said).tone(Tone.SECONDARY)).grow().gap(8)).gap(12).height(520).align("stretch"),
                """
                new ConversationList(conversations).selectedId(selected).onSelect(id -> selected = id)
                new ChatHeader("Ana Reyes").online().onInfo(...)
                new MessageList(messages).me("me").people(people)   // ChatMessage, ChatPerson: typed, generated from the TypeScript
                new ChatComposer().onSend(v -> send(((Map<?, ?>) v).get("text")))""",
                ConversationList.class, ChatHeader.class, MessageList.class, ChatComposer.class);
        gallery.page("Chat", "Voice note", () -> column(
                        new VoiceNote(new VoiceNote.Note(14, null)),
                        text("A voice message's player: its length, play and pause").tone(Tone.SECONDARY)).gap(8),
                """
                new VoiceNote(new VoiceNote.Note(14, null))   // seconds, and where the sound is""",
                VoiceNote.class);
        gallery.page("Chat", "Empty, paged, loading", () -> column(
                        button(empty ? "Show the loading rows" : "Show the empty state").onClick(() -> empty = !empty),
                        empty
                                ? new EmptyState("No orders yet").message("Orders show here once someone buys something.")
                                : new SkeletonRows().rows(4).columns(3),
                        new Pages(page, 5).total(48).onPage(p -> page = p),
                        text("Page " + (int) page).tone(Tone.SECONDARY)).gap(12),
                """
                new EmptyState("No orders yet").message("Orders show here once someone buys something.")
                new SkeletonRows().rows(4).columns(3)
                new Pages(page, 5).total(48).onPage(p -> page = p)""",
                EmptyState.class, SkeletonRows.class, Pages.class);
    }

    private static String title(String id) {
        return CONVERSATIONS.stream().filter(c -> c.id().equals(id)).map(Conversation::title).findFirst().orElse(id);
    }
}
