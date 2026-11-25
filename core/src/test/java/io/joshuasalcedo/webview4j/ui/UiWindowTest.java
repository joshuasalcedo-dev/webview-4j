package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A screen written only in Java, shown with the real components, its handlers run in Java. */
class UiWindowTest {

    static final class Counter implements Screen {
        int count;
        String name = "";
        boolean sync;

        @Override
        public Component<?> render() {
            return frame(column(
                    title("Clicked " + count + " times").key("title"),
                    button("Click me").accent().onClick(() -> count++).key("click"),
                    textField("Name").value(name).onChange(v -> name = v).key("name"),
                    toggle("Sync").checked(sync).onChange(v -> sync = v),
                    text(name.isEmpty() ? "Nobody" : "Hello, " + name)).padding(16))
                    .statusBar(statusBar(statusItem("Ready").dot(DotState.ONLINE)));
        }
    }

    @Test
    void aTreeNamesItsHandlersByWhereTheyAre() {
        Counter screen = new Counter();
        Map<String, Consumer<Object>> handlers = new LinkedHashMap<>();
        Map<String, Object> tree = screen.render().toTree(handlers);
        assertEquals("frame", tree.get("t"));
        assertEquals(List.of("0.box/kclick.button:onClick", "0.box/kname.textField:onChange", "0.box/3.switch:onChange"),
                List.copyOf(handlers.keySet()), "by place (key, or place among siblings) and event");
        handlers.get("0.box/kclick.button:onClick").accept(null);
        assertEquals(1, screen.count);
        handlers.get("0.box/kname.textField:onChange").accept("Ada");
        assertEquals("Ada", screen.name);
        handlers.get("0.box/3.switch:onChange").accept(true);
        assertTrue(screen.sync);

        // drawn again with a line more above them: the same ids still reach the same handlers
        screen.name = "";
        Map<String, Consumer<Object>> again = new LinkedHashMap<>();
        screen.render().toTree(again);
        assertEquals(handlers.keySet(), again.keySet());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void aJavaScreenShowsWithTheRealComponentsAndRunsItsHandlersInJava() throws Exception {
        try (App app = App.start()) {
            Counter screen = new Counter();
            UiWindow window = Ui.open(app, screen, o -> o.headless(true).size(600, 400)).join();
            View view = window.view();
            String clicked = "document.body.innerText.match(/Clicked \\d+ times/)?.[0] ?? null";

            assertEquals("Clicked 0 times", until(view, clicked, "Clicked 0 times"));
            // a click in the page runs the Java handler, and the screen shows again
            view.eval("[...document.querySelectorAll('button')].find(b => b.textContent === 'Click me').click()").join();
            assertEquals("Clicked 1 times", until(view, clicked, "Clicked 1 times"));
            assertEquals(1, screen.count);

            // typing goes to Java as it happens
            view.eval("""
                    (() => { const input = document.querySelector('input:not([type=checkbox])');
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(input, 'Ada'); input.dispatchEvent(new Event('input', { bubbles: true })); })()
                    """).join();
            String hello = "document.body.innerText.includes('Hello, Ada') ? 'yes' : null";
            assertEquals("yes", until(view, hello, "yes"));
            assertEquals("Ada", screen.name);

            // a change from elsewhere shows after refresh()
            synchronized (screen) {
                screen.count = 41;
            }
            window.refresh().join();
            assertEquals("Clicked 41 times", until(view, clicked, "Clicked 41 times"));
            assertTrue(((String) view.eval("document.body.innerText").join()).contains("Ready"), "the status bar shows");
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void whatGoesWrongInAHandlerIsToldToThePersonInADialog() throws Exception {
        try (App app = App.start()) {
            Screen screen = () -> column(
                    button("Save").onClick(() -> {
                        throw new UiException("Name is required", "Type the name people will see.");
                    }),
                    button("Crash").onClick(() -> {
                        throw new IllegalStateException("database pool exhausted");
                    }));
            UiWindow window = Ui.open(app, screen, o -> o.headless(true).size(600, 400)).join();
            View view = window.view();
            String dialog = "document.querySelector('[role=dialog],[role=alertdialog],dialog')?.innerText ?? null";
            String clickSave = "[...document.querySelectorAll('button')].find(b => b.textContent === 'Save').click()";
            assertEquals("ok", until(view, "document.querySelectorAll('button').length >= 2 ? 'ok' : null", "ok"));

            view.eval(clickSave).join();
            String said = (String) untilContains(view, dialog, "Type the name people will see.");
            assertTrue(said.contains("Name is required"), said);

            view.eval("document.querySelector('[role=dialog] button,[role=alertdialog] button,dialog button')?.click()").join();
            assertEquals("gone", until(view, dialog.replace("?? null", "?? 'gone'"), "gone"));

            view.eval("[...document.querySelectorAll('button')].find(b => b.textContent === 'Crash').click()").join();
            String generic = (String) untilContains(view, dialog, "Something went wrong");
            assertFalse(generic.contains("database pool"), "the technical message is folded under the details: " + generic);
            // the technical message is under "Show details", as the person would open it
            view.eval("[...document.querySelectorAll('button')].find(b => b.textContent === 'Show details').click()").join();
            untilContains(view, dialog, "database pool exhausted");
        }
    }

    private static Object untilContains(View view, String script, String part) throws InterruptedException {
        Object value = null;
        for (int i = 0; i < 100; i++) {
            value = view.eval(script).join();
            if (value instanceof String s && s.contains(part)) return value;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "'; last: " + value);
    }

    /** The script's value once it is {@code expected} (the page loads and answers after the call), or its last. */
    private static Object until(View view, String script, Object expected) throws InterruptedException {
        Object value = null;
        for (int i = 0; i < 150; i++) {
            value = view.eval(script).join();
            if (expected.equals(value)) return value;
            Thread.sleep(100);
        }
        return value;
    }
}
