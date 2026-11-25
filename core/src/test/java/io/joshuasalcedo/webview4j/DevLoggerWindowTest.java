package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * The dev logger in a real (hidden) window, as the starter opens it: an app's page calls Java, logs and fetches,
 * Java logs, and the logger's page gets each of them, and draws them (egui-4j's dev-logger on its canvas).
 */
// A real window: Windows here (macOS's event loop needs the main thread, which a test does not have).
@org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
class DevLoggerWindowTest {

    private static Object until(View view, String script, Predicate<Object> done) throws InterruptedException {
        Object last = null;
        for (int i = 0; i < 100; i++) {
            last = view.eval(script).join();
            if (done.test(last)) return last;
            Thread.sleep(100);
        }
        return last;
    }

    @Test
    void whatAnAppDoesReachesTheLoggersPage() throws Exception {
        try (App app = App.start()) {
            app.handle("todos.add", call -> Map.of("id", 7, "title", call.text("title")));
            // the logger page's own console (left out of the logger, but the test reads it)
            java.util.Queue<String> ownConsole = new java.util.concurrent.ConcurrentLinkedQueue<>();
            app.subscribe(LogRecord.class, r -> { if (r.source() == LogRecord.Source.PAGE) ownConsole.add(r.view() + " " + r.message()); });
            DevLogger logger = DevLogger.open(app, options -> options.headless(true)).join();
            View own = logger.view();
            // what the logger's page is sent, kept where the test can read it
            own.eval("window.__got = []; window.__webview4j.listen('" + DevLogger.TOPIC + "', d => window.__got.push(...d))").join();

            View page = app.open(WindowOptions.html("<p>app</p>").headless(true)).join();
            logger.watch(page).join();
            page.eval("window.__webview4j.invoke('todos.add', {title: 'milk'})").join();
            page.eval("console.warn('slow')").join();
            page.eval("fetch('" + App.UI_URL + "ui.css').then(r => r.text())").join();
            java.util.logging.Logger.getLogger("app.todos").info("saved 1 todo");
            own.eval("console.info('from the logger itself')").join();

            String kinds = "JSON.stringify(window.__got.map(e => e.kind + ' ' + (e.name || e.message || e.url)))";
            Object got = until(own, kinds, v -> String.valueOf(v).contains("todos.add") && String.valueOf(v).contains("slow")
                    && String.valueOf(v).contains("saved 1 todo") && String.valueOf(v).contains("ui.css"));
            String seen = String.valueOf(got);
            assertTrue(seen.contains("bridge todos.add"), seen);
            assertTrue(seen.contains("log slow"), seen);
            assertTrue(seen.contains("log saved 1 todo"), seen);
            assertTrue(seen.contains("http " + App.UI_URL + "ui.css"), seen);
            assertFalse(seen.contains("from the logger itself"), "its own page is not the app's: " + seen);

            // the call to Java, with what was sent and answered
            Object call = own.eval("JSON.stringify(window.__got.find(e => e.kind === 'bridge'))").join();
            @SuppressWarnings("unchecked")
            Map<String, Object> bridge = (Map<String, Object>) Json.parse(String.valueOf(call));
            assertEquals("toJava", bridge.get("direction"));
            assertEquals(Map.of("headers", Map.of(), "body", Map.of("title", "milk")), bridge.get("request"));
            assertEquals(Map.of("headers", Map.of(), "body", Map.of("id", 7L, "title", "milk")), bridge.get("response"));

            // and the page drew them: egui-4j started on its canvas (its wasm loaded), and said so on its console
            for (int i = 0; i < 100 && ownConsole.stream().noneMatch(l -> l.startsWith(own.id() + " ") && l.contains("dev-logger is shown")); i++) {
                Thread.sleep(100);
            }
            assertTrue(ownConsole.stream().anyMatch(l -> l.startsWith(own.id() + " ") && l.contains("[egui4j::web] dev-logger is shown")),
                    "egui-4j started: " + ownConsole);
            Object failed = own.eval("document.querySelector('[role=alert]')?.textContent ?? null").join();
            assertEquals(null, failed, "the page shows no error");

            // everything is in this run's file, and the page's search searches it
            List<String> lines = java.nio.file.Files.readAllLines(logger.file());
            assertTrue(lines.size() >= 4, "the file: " + lines);
            own.eval("window.__webview4j.invoke('" + DevLogger.SEARCH + "', {text: 'MILK'})"
                    + ".then(f => window.__found = JSON.stringify(f.map(e => e.kind + ' ' + (e.name || e.message || e.url))))").join();
            Object found = until(own, "window.__found ?? null", v -> v != null);
            assertEquals("[\"bridge todos.add\"]", String.valueOf(found), "searched, ignoring case");
            assertEquals(List.of(), logger.search("   "));

            java.nio.file.Path file = logger.file();
            logger.close();
            // a new logger, a new run: the file starts empty
            DevLogger again = DevLogger.open(app, options -> options.headless(true)).join();
            assertEquals(file, again.file());
            assertEquals(List.of(), java.nio.file.Files.readAllLines(again.file()));
            again.close();
            assertTrue(List.of(own).stream().allMatch(v -> v.id() != null));
        }
    }
}
