package io.joshuasalcedo.swaswa.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.ui.button.UiButton;
import io.joshuasalcedo.swaswa.ui.layout.UiHBox;
import io.joshuasalcedo.swaswa.ui.segmentedcontrol.UiSegmentedControl;
import io.joshuasalcedo.swaswa.ui.switch_.UiSwitch;
import io.joshuasalcedo.swaswa.ui.text.UiTextField;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** swaswa-ui's controls from Java: served from the jar, the system's look (Windows 11 here), events and values back. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class UiTest {

    static <T> T until(Supplier<T> value, Predicate<T> done) throws InterruptedException {
        T v = value.get();
        for (int i = 0; i < 200 && !done.test(v); i++) {
            Thread.sleep(50);
            v = value.get();
        }
        return v;
    }

    static Object js(View view, String script) {
        return view.eval(script).join();
    }

    @Test
    void everyLibrarysButtonsTogglesAndInputsShareTheirRoles() {
        // a button is a button, whichever library drew it: HTML's, swaswa-ui's
        List<io.joshuasalcedo.swaswa.component.ButtonBase<?>> buttons = List.of(new io.joshuasalcedo.swaswa.html.Button("Save"),
                new UiButton("Send"), new io.joshuasalcedo.swaswa.ui.toolbar.UiToolbarButton());
        assertEquals(3, buttons.size());
        io.joshuasalcedo.swaswa.component.TextInput<?> name = new UiTextField();
        name.setPlaceholder("Ana");
        name.setValue("Bo");
        assertEquals("Bo", name.valueProperty().get());
        io.joshuasalcedo.swaswa.component.TextInput<?> html = new io.joshuasalcedo.swaswa.html.Input();
        html.setValue("Cy");
        assertEquals("Cy", html.valueProperty().get());
        io.joshuasalcedo.swaswa.component.Toggle<?> wifi = new UiSwitch();
        wifi.setSelected(true);
        assertTrue(((UiSwitch) wifi).checkedProperty().get(), "a toggle's selected is its checked");
        io.joshuasalcedo.swaswa.component.Choice<?> show = new UiSegmentedControl();
        show.setValue("open");
        assertEquals("open", show.valueProperty().get());
    }

    @Test
    void dataRecordsComponentsAndChoicesAreProps() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<!doctype html><body>").title("swaswa test: ui props").size(700, 500)).join();
            Document doc = Document.of(view);

            // a list of records: the segments
            UiSegmentedControl show = new UiSegmentedControl();
            show.setSegments(List.of(new UiSegmentedControl.Segment("open", "Open"), new UiSegmentedControl.Segment("shipped", "Shipped")));
            show.valueProperty().set("open");

            // a component as a prop: the field's label, a Java span
            UiTextField name = new UiTextField();
            name.setLabel(Html.span("Customer").setId("label-span"));

            // one of two kinds: padding as a record of sides, or a number
            UiHBox row = new UiHBox();
            row.setPaddingProp(new UiHBox.Padding(4.0, null, 12.0, null));
            row.add(Html.span("in the row").setId("in-row"));

            doc.body().add(show);
            doc.body().add(name);
            doc.body().add(row);
            doc.flush().join();

            assertEquals("Open,Shipped", until(() -> js(view, "[...document.querySelectorAll('.nt-segment')].map(b => b.textContent).join()"),
                    "Open,Shipped"::equals), "the records, drawn as segments");
            assertEquals("true", js(view, "document.querySelector('.nt-segment').getAttribute('aria-checked')"));
            js(view, "[...document.querySelectorAll('.nt-segment')][1].click()");
            assertEquals("shipped", until(() -> show.valueProperty().get(), "shipped"::equals), "the chosen one, in Java");

            assertEquals(Boolean.TRUE, until(() -> js(view, "!!document.querySelector('.nt-field-label #label-span')"), Boolean.TRUE::equals),
                    "Java's span, where the field draws its label");
            assertEquals(Boolean.TRUE, js(view, "document.getElementById('label-span').getClientRects().length > 0"), "and shown");

            // the box the row draws: the nearest element up from the span with a padding set
            String box = "(() => { let e = document.getElementById('in-row'); while (e && !e.style.padding && !e.style.paddingTop) e = e.parentElement; return e; })()";
            assertEquals("4px 0px 12px", until(() -> String.valueOf(js(view, "(() => { const s = getComputedStyle(" + box
                    + "); return s.paddingTop + ' ' + s.paddingRight + ' ' + s.paddingBottom; })()")), "4px 0px 12px"::equals),
                    "the record's sides; those left out, left out");

            row.setPaddingProp(8);   // the other kind: a number
            doc.flush().join();
            assertEquals("8px", until(() -> String.valueOf(js(view, "getComputedStyle(" + box + ").paddingTop")), "8px"::equals));
        }
    }

    @Test
    void controlsLookLikeTheSystemAndAnswerJava() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<!doctype html><body>").title("swaswa test: ui").size(700, 500)).join();
            Document doc = Document.of(view);
            List<String> clicked = new CopyOnWriteArrayList<>();
            UiButton send = new UiButton("Send");
            send.setVariant(UiButton.Variant.ACCENT);
            send.onClick(e -> clicked.add("send"));
            UiSwitch wifi = new UiSwitch();
            wifi.setLabel("Wi-Fi");
            io.joshuasalcedo.swaswa.state.Session session = new io.joshuasalcedo.swaswa.state.Session();
            io.joshuasalcedo.swaswa.state.Store<Boolean> notify =
                    io.joshuasalcedo.swaswa.state.Store.of("notify", Boolean.class, () -> true);
            UiSwitch bound = new UiSwitch();
            bound.setLabel("Notify");
            bound.checkedProperty().bindBidirectional(notify.in(session));   // before the page: on from the start
            doc.body().add(bound);
            UiTextField name = new UiTextField();
            name.setLabel("Name");
            doc.body().add(send);
            doc.body().add(wifi);
            doc.body().add(name);
            doc.flush().join();

            String button = "document.querySelector('.nt-button')";
            assertEquals("Send", until(() -> js(view, button + "?.textContent ?? ''"), "Send"::equals),
                    "loaded from the jar (served: ui.js and its chunks), drawn");
            assertEquals("windows", js(view, "document.querySelector('.nt-root')?.dataset.platform"),
                    "inside a NativeProvider of its own: the system's look");
            assertEquals(Boolean.TRUE, until(() -> js(view, "document.styleSheets.length > 0 && getComputedStyle(" + button
                    + ").fontFamily.includes('Segoe UI')"), Boolean.TRUE::equals), "ui.css is in the page: Windows' own font");
            js(view, button + ".click()");
            assertEquals(List.of("send"), until(() -> clicked, l -> !l.isEmpty()));

            assertEquals(Boolean.TRUE, until(() -> js(view, "[...document.querySelectorAll('.nt-switch')].find(s => s.textContent.includes('Notify'))?.querySelector('input').checked"), Boolean.TRUE::equals),
                    "bound before the page: the store's true, drawn");
            js(view, "[...document.querySelectorAll('.nt-switch')].find(s => s.textContent.includes('Wi-Fi')).querySelector('input').click()");
            assertEquals(Boolean.TRUE, until(() -> wifi.checkedProperty().get(), Boolean.TRUE::equals), "the switch, in Java");
            wifi.checkedProperty().set(false);
            doc.flush().join();
            assertEquals(Boolean.FALSE, until(() -> js(view, "[...document.querySelectorAll('.nt-switch')].find(s => s.textContent.includes('Wi-Fi')).querySelector('input').checked"), Boolean.FALSE::equals),
                    "Java's, in the page");
            wifi.checkedProperty().set(true);
            doc.flush().join();
            assertEquals(Boolean.TRUE, until(() -> js(view, "[...document.querySelectorAll('.nt-switch')].find(s => s.textContent.includes('Wi-Fi')).querySelector('input').checked"), Boolean.TRUE::equals));
            assertTrue(String.valueOf(js(view, "document.querySelector('link[data-swaswa-library]').href")).contains("ui.css?v="),
                    "the style sheet's URL has its version");

            js(view, """
                    (() => { const f = document.querySelector('.nt-field input');
                      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(f, 'Ana');
                      f.dispatchEvent(new Event('input', { bubbles: true })); })()""");
            assertEquals("Ana", until(() -> name.valueProperty().get(), "Ana"::equals), "typed, in Java");
        }
    }

    /** A chat service in memory: one conversation, two messages; what is sent is kept; events kept to push. */
    static final class MemoryChat implements io.joshuasalcedo.swaswa.ui.chat.ChatService {
        final List<io.joshuasalcedo.swaswa.ui.chat.ChatTypes.OutgoingMessage> sent = new CopyOnWriteArrayList<>();
        final List<java.util.function.Consumer<io.joshuasalcedo.swaswa.ui.chat.ChatEvent>> listeners = new CopyOnWriteArrayList<>();

        @Override
        public java.util.concurrent.CompletableFuture<io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPage<io.joshuasalcedo.swaswa.ui.chat.ChatTypes.Conversation>> loadConversations(String cursor, String category) {
            var last = new io.joshuasalcedo.swaswa.ui.chat.ChatTypes.LastMessage("See you at 3", false, java.time.Instant.now());
            return java.util.concurrent.CompletableFuture.completedFuture(io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPage.of(List.of(
                    new io.joshuasalcedo.swaswa.ui.chat.ChatTypes.Conversation("c1", "Ana Reyes", last, 1))));
        }

        @Override
        public java.util.concurrent.CompletableFuture<io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPage<io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage>> loadMessages(String conversationId, String cursor) {
            return java.util.concurrent.CompletableFuture.completedFuture(io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatPage.of(List.of(
                    io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage.text("m1", "ana", "Hi! Are we still on?"),
                    io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage.text("m2", "me", "Yes, see you at 3"))));
        }

        @Override
        public java.util.concurrent.CompletableFuture<io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage> sendMessage(io.joshuasalcedo.swaswa.ui.chat.ChatTypes.OutgoingMessage message) {
            sent.add(message);
            var stored = new io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage("m" + (10 + sent.size()), "me", null, message.text(),
                    java.time.Instant.now(), "sent", null, null, null, null, null, message.clientId());
            return java.util.concurrent.CompletableFuture.completedFuture(stored);
        }

        @Override
        public void events(java.util.function.Consumer<io.joshuasalcedo.swaswa.ui.chat.ChatEvent> emit) {
            listeners.add(emit);
        }
    }

    @Test
    void aChatWindowTalksToAJavaService() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<!doctype html><body>").title("swaswa test: ui chat").size(1000, 700)).join();
            Document doc = Document.of(view);
            MemoryChat service = new MemoryChat();
            io.joshuasalcedo.swaswa.ui.chat.UiChatWindow chat = new io.joshuasalcedo.swaswa.ui.chat.UiChatWindow();
            chat.setAdapter(service.asAdapter());
            chat.setDefaultOpenId("c1");
            chat.setTitleBar(false);
            doc.body().add(chat);
            doc.flush().join();

            String text = "document.body.innerText";
            assertTrue(String.valueOf(until(() -> js(view, text), t -> String.valueOf(t).contains("Ana Reyes"))).contains("Ana Reyes"),
                    "the conversations, from Java");
            assertTrue(String.valueOf(until(() -> js(view, text), t -> String.valueOf(t).contains("Are we still on?"))).contains("Are we still on?"),
                    "the open conversation's messages, from Java");

            // the person types and sends (Enter)
            js(view, """
                    (() => { const box = document.querySelector('textarea');
                      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(box, 'On my way!');
                      box.dispatchEvent(new Event('input', { bubbles: true }));
                      box.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })); })()""");
            assertEquals("On my way!", until(() -> service.sent.isEmpty() ? null : service.sent.getFirst().text(), "On my way!"::equals),
                    "sent through Java's service");
            assertEquals("c1", service.sent.getFirst().conversationId());

            // Java tells the window a message came
            until(() -> service.listeners, l -> !l.isEmpty());
            service.listeners.getFirst().accept(new io.joshuasalcedo.swaswa.ui.chat.ChatEvent.MessageReceived("c1",
                    io.joshuasalcedo.swaswa.ui.chat.ChatTypes.ChatMessage.text("m20", "ana", "Great, the door code is 4512")));
            assertTrue(String.valueOf(until(() -> js(view, text), t -> String.valueOf(t).contains("door code is 4512"))).contains("door code is 4512"),
                    "a message Java pushed, shown");
        }
    }
}
