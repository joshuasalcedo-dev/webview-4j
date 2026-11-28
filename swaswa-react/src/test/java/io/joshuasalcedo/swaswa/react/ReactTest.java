package io.joshuasalcedo.swaswa.react;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.PageScript;
import io.joshuasalcedo.swaswa.state.Session;
import io.joshuasalcedo.swaswa.state.Store;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Real React components in the page, from Java: props, events, values the person changes, children, state. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class ReactTest {

    static final PageScript LIBRARY = PageScript.resource("react:test", ReactTest.class, "test-library.js");

    // what a library's generated classes look like
    static final class Counter extends ReactElement<Counter> {
        Counter(String label) { super("Counter", false); prop("label", label); }
        Counter setCount(int count) { return prop("count", count); }
        Counter onIncrement(ReactListener l) { return onEvent("onIncrement", l); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    static final class TextField extends ReactElement<TextField> {
        TextField() { super("TextField", false); bind("value", "onChange"); }
        Property<String> valueProperty() { return propProperty("value", String.class); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    static final class Switch extends ReactElement<Switch> {
        Switch() { super("Switch", false); bind("checked", "onCheckedChange"); }
        Property<Boolean> checkedProperty() { return propProperty("checked", Boolean.class); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    static final class Card extends ReactElement<Card> {
        Card(String title) { super("Card", true); prop("title", title); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    static final class Group extends ReactElement<Group> {
        Group(String name) { super("Group", true); prop("name", name); }
        Group setName(String name) { return prop("name", name); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    static final class Item extends ReactElement<Item> {
        Item(String id) { super("Item", false); prop("itemId", id); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    // written by hand: the base converts what it is given, whatever made the class
    static final class Stamp extends ReactElement<Stamp> {
        Stamp() { super("Stamp", false); }
        Stamp setDay(java.time.LocalDate day) { return prop("day", day); }
        Stamp setAt(java.time.Instant at) { return prop("at", at); }
        Stamp setBytes(byte[] bytes) { return prop("bytes", bytes); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    record InboxPage(String title, int count) {}

    record Message(String text) {}

    static final class Inbox extends ReactElement<Inbox> {
        Inbox(ReactAdapter service) {
            super("Inbox", false);
            adapter("service", List.of("queries.load"), List.of("events.subscribe"), service);
        }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    static final class Counter2 extends ReactElement<Counter2> {
        Counter2() { super("Counter2", false); handleProp("actionsRef"); }
        java.util.concurrent.CompletableFuture<Object> add(int by) { return handle("actionsRef", "add", by); }
        java.util.concurrent.CompletableFuture<Object> twice(int x) { return handle("actionsRef", "twice", x); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    record Row(String id, String name) {}

    static final class Rows extends ReactElement<Rows> {
        Rows(List<Row> rows) { super("Rows", false); prop("rows", rows); }
        Rows setRowMenu(java.util.function.Function<Row, List<MenuItem>> menu) { return menu("rowMenu", Row.class, menu); }
        @Override protected List<PageScript> libraries() { return List.of(LIBRARY); }
    }

    static <T> T until(Supplier<T> value, Predicate<T> done) throws InterruptedException {
        T v = value.get();
        for (int i = 0; i < 100 && !done.test(v); i++) {
            Thread.sleep(50);
            v = value.get();
        }
        return v;
    }

    static Object js(View view, String script) {
        return view.eval(script).join();
    }

    static View page(App app, String title) {
        return app.open(WindowOptions.html("<!doctype html><body>").title(title).size(500, 400)).join();
    }

    @Test
    void aComponentDrawsFromJavaAndSendsItsEventsBack() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: react");
            Document doc = Document.of(view);
            List<Integer> asked = new CopyOnWriteArrayList<>();
            Counter counter = new Counter("Clicks");
            counter.onIncrement(e -> {
                int next = e.payload(Integer.class);
                asked.add(next);
                counter.setCount(next);   // Java decides: the page draws it
            });
            doc.body().add(counter);
            doc.flush().join();

            assertEquals("Clicks: 0", until(() -> js(view, "document.getElementById('counter')?.textContent ?? ''"), "Clicks: 0"::equals));
            js(view, "document.getElementById('counter').click()");
            assertEquals("Clicks: 1", until(() -> js(view, "document.getElementById('counter').textContent"), "Clicks: 1"::equals));
            js(view, "document.getElementById('counter').click()");
            assertEquals("Clicks: 2", until(() -> js(view, "document.getElementById('counter').textContent"), "Clicks: 2"::equals));
            assertEquals(List.of(1, 2), asked);

            doc.body().remove(counter);
            doc.flush().join();
            assertEquals(Boolean.FALSE, until(() -> js(view, "!!document.getElementById('counter')"), Boolean.FALSE::equals), "unmounted");
        }
    }

    @Test
    void whatThePersonChangesIsKeptInJavaAtOnce() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: react values");
            Document doc = Document.of(view);
            Session session = new Session();
            Store<Boolean> dark = Store.of("dark", Boolean.class, () -> false);

            TextField name = new TextField();
            Switch sw = new Switch();
            sw.checkedProperty().bindBidirectional(dark.in(session));
            doc.body().add(name);
            doc.body().add(sw);
            doc.flush().join();
            until(() -> js(view, "!!document.getElementById('field')"), Boolean.TRUE::equals);

            // typing, as a person does: React's onChange (the value setter, then an input event)
            js(view, """
                    (() => { const f = document.getElementById('field');
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(f, 'Ana'); f.dispatchEvent(new Event('input', { bubbles: true })); })()""");
            assertEquals("Ana", until(() -> name.valueProperty().get(), "Ana"::equals), "kept in Java");
            assertEquals("Ana", js(view, "document.getElementById('field').value"), "and the page keeps it: no round trip");

            name.valueProperty().set("Bo");   // Java's change is drawn
            doc.flush().join();
            assertEquals("Bo", until(() -> js(view, "document.getElementById('field').value"), "Bo"::equals));

            js(view, "document.getElementById('switch').click()");
            assertEquals(Boolean.TRUE, until(() -> dark.get(session), Boolean.TRUE::equals), "the switch sets the store it is bound to");
            dark.set(session, false);          // and the store sets the switch
            doc.flush().join();
            assertEquals("off", until(() -> js(view, "document.getElementById('switch').textContent"), "off"::equals));
        }
    }

    @Test
    void javaChildrenGoWhereTheComponentPutsItsChildren() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: react children");
            Document doc = Document.of(view);
            Card card = new Card("Orders");
            card.add(Html.p("three open").setId("inside"));
            doc.body().add(card);
            doc.flush().join();

            assertEquals(Boolean.TRUE, until(() -> js(view, "!!document.querySelector('#card .body #inside')"), Boolean.TRUE::equals),
                    "Java's paragraph inside the card's body");
            card.add(Html.p("one late").setId("late"));
            doc.flush().join();
            assertEquals(Boolean.TRUE, until(() -> js(view, "!!document.querySelector('#card .body #late')"), Boolean.TRUE::equals),
                    "added later, there too");
            assertEquals("Orders", js(view, "document.querySelector('#card h3').textContent"));
        }
    }

    @Test
    void aComponentInsideAnotherIsInItsTreeAndSeesItsContext() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: react context");
            Document doc = Document.of(view);
            Group group = new Group("alpha");
            group.add(Html.div(new Item("first")));   // a plain element between them
            doc.body().add(group);
            doc.flush().join();
            assertEquals("alpha", until(() -> js(view, "document.getElementById('first')?.textContent ?? ''"), "alpha"::equals),
                    "the group's context, through Java's div");
            assertEquals(Boolean.TRUE, js(view, "!!document.querySelector('#group #first')"), "and where Java put it");

            group.add(new Item("second"));
            group.setName("beta");
            doc.flush().join();
            assertEquals("beta", until(() -> js(view, "document.getElementById('second')?.textContent ?? ''"), "beta"::equals));
            assertEquals("beta", js(view, "document.getElementById('first').textContent"), "every item follows");
            assertEquals(1L, ((Number) js(view, "document.querySelectorAll('#group').length")).longValue(), "one group: no second root");
        }
    }

    @Test
    void datesBytesAndItsStyleReachTheComponentAsTheyAre() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: react values");
            Document doc = Document.of(view);
            Stamp stamp = new Stamp();
            stamp.setDay(java.time.LocalDate.of(2026, 11, 27));
            stamp.setAt(java.time.Instant.parse("2026-11-27T10:15:30Z"));
            stamp.setBytes(new byte[] {1, 2, 3, 4});
            stamp.getStyle().set(io.joshuasalcedo.swaswa.css.CssProperty.BACKGROUND_COLOR, "rgb(255, 0, 0)");
            doc.body().add(stamp);
            doc.flush().join();

            assertEquals("2026-11-27", until(() -> js(view, "document.getElementById('stamp-day')?.textContent ?? ''"), "2026-11-27"::equals),
                    "a LocalDate: the page's Date, that day");
            assertEquals("2026-11-27T10:15:30.000Z", js(view, "document.getElementById('stamp-at').textContent"), "an Instant: that moment");
            assertEquals("10", until(() -> js(view, "document.getElementById('stamp-bytes').textContent"), "10"::equals),
                    "bytes: served by the app, fetched into an ArrayBuffer (1+2+3+4)");
            assertEquals("rgb(255, 0, 0)", js(view, "getComputedStyle(document.getElementById('stamp')).backgroundColor"),
                    "its getStyle(): the component's own style");
            assertEquals("contents", js(view, "getComputedStyle(document.querySelector('[data-swaswa-react=Stamp]')).display"),
                    "its own element still lays out as its parent's");

            stamp.setBytes(new byte[] {9});
            doc.flush().join();
            assertEquals("9", until(() -> js(view, "document.getElementById('stamp-bytes').textContent"), "9"::equals), "new bytes");
        }
    }

    @Test
    void anAdapterIsAnsweredByJavaAndHearsItsEvents() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: react adapter");
            Document doc = Document.of(view);
            List<ReactAdapter.ReactEmitter> listening = new CopyOnWriteArrayList<>();
            ReactAdapter service = new ReactAdapter() {
                @Override
                public java.util.concurrent.CompletableFuture<?> call(ReactCall call) {
                    String box = call.arg(0, String.class);
                    if (box.equals("broken")) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("You're offline."));
                    return java.util.concurrent.CompletableFuture.supplyAsync(() -> new InboxPage("Inbox", 3));   // later, on another thread
                }

                @Override
                public void subscribe(String method, ReactEmitter emitter) {
                    listening.add(emitter);
                }
            };
            doc.body().add(new Inbox(service));
            doc.flush().join();

            assertEquals("Inbox:3", until(() -> js(view, "document.getElementById('inbox-title')?.textContent ?? ''"), "Inbox:3"::equals),
                    "the query answered by Java, a record");
            assertEquals("You're offline.", until(() -> js(view, "document.getElementById('inbox-error').textContent"), "You're offline."::equals),
                    "a failed future fails the promise, with its message");
            until(() -> listening, l -> !l.isEmpty());
            listening.getFirst().emit(new Message("hello from Java"));
            assertEquals("hello from Java", until(() -> js(view, "document.getElementById('inbox-last').textContent"), "hello from Java"::equals),
                    "an event Java pushed");
        }
    }

    @Test
    void javaCallsAComponentsHandleAndItsMenusAreReady() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: react handles and menus");
            Document doc = Document.of(view);
            Counter2 counter = new Counter2();
            List<String> chosen = new CopyOnWriteArrayList<>();
            Rows rows = new Rows(List.of(new Row("a1", "Apples"), new Row("b2", "Bread")));
            rows.setRowMenu(row -> List.of(MenuItem.of("Open " + row.name(), () -> chosen.add("open " + row.id())), MenuItem.separator(),
                    MenuItem.of("Delete", () -> chosen.add("delete " + row.id()))));
            doc.body().add(counter);
            doc.body().add(rows);
            doc.flush().join();
            until(() -> js(view, "document.getElementById('counter2')?.textContent ?? ''"), "0"::equals);

            counter.add(5).join();
            assertEquals("5", until(() -> js(view, "document.getElementById('counter2').textContent"), "5"::equals), "Java called add(5)");
            assertEquals(84L, ((Number) counter.twice(42).join()).longValue(), "and got what it returned");

            assertEquals("Open Bread,Delete", until(() -> js(view, "document.getElementById('row-b2')?.dataset.menu ?? ''"), "Open Bread,Delete"::equals),
                    "each row's menu, made in Java, ready when the component asks");
            js(view, "document.getElementById('row-b2').click()");
            assertEquals(List.of("open b2"), until(() -> chosen, l -> !l.isEmpty()), "the chosen entry's action ran in Java");
        }
    }
}
