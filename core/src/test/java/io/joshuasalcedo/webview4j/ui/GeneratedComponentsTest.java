package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.ComponentsTest.until;
import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.components.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The components java-gen made from their React props (core/src/main/ui/java-gen.json): the tree Java sends is
 * what the generated renderer reads, each one draws in a real window, and its events reach Java typed.
 */
class GeneratedComponentsTest {


    /** The tree as the page gets it (toTree is Component's, in this package). */
    static Map<String, Object> tree(Component<?> component) {
        return component.toTree(new HashMap<>());
    }

    @Test
    void theGeneratedDataTypesWriteWhatThePageReads() {
        // a record: the page's names; what is left out is absent, not null
        assertEquals("{\"id\":\"team\",\"label\":\"Team\",\"color\":\"blue\"}",
                io.joshuasalcedo.webview4j.Json.write(new ChatTag("team", "Team").withColor(PillColor.BLUE)),
                "PillColor (hand-written, reused) writes the page's word");
        assertEquals("{\"id\":\"team\",\"label\":\"Team\"}", io.joshuasalcedo.webview4j.Json.write(new ChatTag("team", "Team")));
        // an enum made from a string union: the page's word, not Java's name
        assertEquals("\"delivered\"", io.joshuasalcedo.webview4j.Json.write(DeliveryState.DELIVERED));
        // a sealed union: each member writes the word that tells it apart
        assertEquals("{\"kind\":\"voice\",\"duration\":3.5}",
                io.joshuasalcedo.webview4j.Json.write(new ChatAttachment.Voice(3.5, null)));
        // nested all the way: a message with an attachment, read back as the page would
        Map<String, Object> m = io.joshuasalcedo.webview4j.Json.object(io.joshuasalcedo.webview4j.Json.write(
                new ChatMessage("m1", "ana", "2026-11-25T09:00:00Z").withText("hi")
                        .withAttachments(List.of(new ChatAttachment.File("a.pdf", 1200.0, null)))));
        assertEquals("hi", m.get("text"));
        assertFalse(m.containsKey("pinned"), "optional and left out: absent");
        assertEquals(Map.of("kind", "file", "name", "a.pdf", "size", 1200.0), ((List<?>) m.get("attachments")).get(0));
    }

    @Test
    void theTreeCarriesThePropsTheirTypesAndTheHandlers() {
        ChatHeader header = new ChatHeader("Ana").online().typing("Ana").status(text("2 online")).onCall(() -> {});
        Map<String, Object> tree = tree(header);
        assertEquals("chatHeader", tree.get("t"));
        @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) tree.get("p");
        assertEquals("Ana", p.get("title"));
        assertEquals(true, p.get("online"));
        assertEquals("Ana", p.get("typing"));
        assertInstanceOf(String.class, p.get("onCall"), "a handler goes as its id");
        @SuppressWarnings("unchecked") List<Map<String, Object>> children = (List<Map<String, Object>>) tree.get("c");
        @SuppressWarnings("unchecked") Map<String, Object> status = (Map<String, Object>) children.get(0).get("p");
        assertEquals("status", status.get("slot"), "a component in a named place is a child with its slot");

        // An enum goes as the React prop's own literal.
        Map<String, Object> composer = tree(new ChatComposer().sendKey(ChatComposer.SendKey.MOD_ENTER));
        @SuppressWarnings("unchecked") Map<String, Object> cp = (Map<String, Object>) composer.get("p");
        assertEquals("mod+enter", cp.get("sendKey"));
    }

    static final class Chat implements Screen {
        double page = 1;
        String sent = "";

        @Override
        public Component<?> render() {
            return column(
                    new EmptyState("Nothing here yet").message("Add the first one"),
                    new Pages(page, 3).total(30).onPage(v -> page = v),
                    text("page " + (int) page),
                    new SkeletonRows().rows(2).columns(3),
                    new ChatHeader("Ana Reyes").online().onInfo(() -> {}),
                    new ConversationList(List.of(new Conversation("c1", "Marco Diaz")
                            .withLastMessage(new Conversation.LastMessage("2026-11-25T09:00:00Z").withText("See you soon")).withUnread(2.0)))
                            .selectedId("c1").search(false),
                    new MessageList(List.of(new ChatMessage("m1", "ana", "2026-11-25T09:01:00Z").withText("Morning, all set?"))).height(160)
                            .me("me").people(Map.of("ana", new ChatPerson("ana", "Ana Reyes"))),
                    new VoiceNote(new VoiceNote.Note(12, null)),
                    new ChatComposer().placeholder("Write a message").onSend(v -> sent = String.valueOf(((Map<?, ?>) v).get("text"))),
                    text("sent " + sent)).padding(16).gap(8).scroll();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(120)
    void eachDrawsInAWindowAndItsEventsReachJava() throws Exception {
        try (App app = App.start()) {
            app.subscribe(io.joshuasalcedo.webview4j.LogRecord.class, log -> {
                if (log.level() == io.joshuasalcedo.webview4j.LogRecord.Level.ERROR) System.out.println("PAGE ERROR " + log.message());
            });
            Chat screen = new Chat();
            View view = Ui.open(app, screen, o -> o.headless(true).size(900, 1100)).join().view();
            String text = until(view, "Morning, all set?");
            assertFalse(text.contains("No component"), "every generated type is known to the renderer: " + text);
            for (String expected : List.of("Nothing here yet", "Add the first one", "Page 1 of 3", "30 in all", "Ana Reyes",
                    "Marco Diaz", "See you soon")) {
                assertTrue(text.contains(expected), "shows '" + expected + "'");
            }

            // a number event: Pages' Next
            view.eval("[...document.querySelectorAll('button')].find(b => b.textContent === 'Next').click()").join();
            until(view, "page 2");
            assertEquals(2, screen.page);

            // an event with parts Java cannot get (files): the text arrives
            view.eval("""
                    (() => { const area = document.querySelector('textarea[placeholder="Write a message"]');
                      const set = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
                      set.call(area, 'Hello from the test'); area.dispatchEvent(new Event('input', { bubbles: true })); })()
                    """).join();
            Thread.sleep(200);
            view.eval("document.querySelector('button[aria-label=Send]').click()").join();
            until(view, "sent Hello from the test");
            assertEquals("Hello from the test", screen.sent);
        }
    }
}
