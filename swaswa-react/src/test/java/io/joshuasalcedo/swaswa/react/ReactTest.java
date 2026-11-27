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
}
