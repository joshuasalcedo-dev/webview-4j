package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The page calls Java and Java pushes to the page, through window.__webview4j in a real, hidden
 * window. The page reports each outcome on its console, which arrives here as page log records.
 */
@EnabledOnOs(OS.WINDOWS)
class BridgeTest {

    record User(long id, String name) {}

    /** Logs what an invoke settles with, as "<label> ok <json>" or "<label> failed <name>: <message>". */
    private static String call(String label, String invoke) {
        return invoke + ".then(v => console.info('" + label + " ok ' + JSON.stringify(v)),"
                + " e => console.info('" + label + " failed ' + e.name + ': ' + e.message))";
    }

    @Test
    @Timeout(90)
    void thePageInvokesJavaCommandsAndListensToWhatJavaEmits() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<String> console = new LinkedBlockingQueue<>();
            app.subscribe(LogRecord.class, log -> {
                if (log.source() == LogRecord.Source.PAGE) console.add(log.message());
            });
            BlockingQueue<Invocation> calls = new LinkedBlockingQueue<>();
            app.handle("users.find", call -> {
                calls.add(call);
                return new User(call.number("id"), "Ada");
            });
            app.handle("users.later", call -> CompletableFuture.supplyAsync(() -> Map.of("later", true)));
            app.handle("users.broken", call -> {
                throw new IllegalStateException("the database is down");
            });
            assertThrows(IllegalStateException.class, () -> app.handle("users.find", call -> null));
            try (Subscription removed = app.handle("users.removed", call -> "never")) {
                assertTrue(removed.isActive());
            }

            View view = app.open(WindowOptions.html("<p>bridge</p>").headless(true)).join();

            view.eval(call("find", "window.__webview4j.invoke('users.find', {id: 42})")).join();
            assertEquals("find ok {\"id\":42,\"name\":\"Ada\"}", console.poll(15, SECONDS));
            Invocation found = calls.poll(5, SECONDS);
            assertEquals(view, found.view(), "the call names the view it came from");
            assertEquals("users.find", found.command());

            view.eval(call("later", "window.__webview4j.invoke('users.later')")).join();
            assertEquals("later ok {\"later\":true}", console.poll(15, SECONDS));

            view.eval(call("broken", "window.__webview4j.invoke('users.broken')")).join();
            assertEquals("broken failed InvokeError: the database is down", console.poll(15, SECONDS));

            view.eval(call("unknown", "window.__webview4j.invoke('users.removed')")).join();
            assertEquals("unknown failed InvokeError: no command 'users.removed'", console.poll(15, SECONDS));

            view.eval(call("bad args", "window.__webview4j.invoke('users.find', {id: 'x'})")).join();
            assertEquals("bad args failed InvokeError: users.find: argument 'id' must be a whole number",
                    console.poll(15, SECONDS));

            // emits before anyone listens are dropped, like calling no listeners
            view.emit("orders.updated", new User(6, "Before")).join();
            view.eval("window.__webview4j.listen('orders.updated', o => console.info('order ' + o.id + ' ' + o.name))").join();
            Thread.sleep(300);   // let the page's first poll reach Rust
            view.emit("orders.updated", new User(7, "Grace")).join();
            view.emit("orders.updated", new User(8, "Hopper")).join();
            assertEquals("order 7 Grace", console.poll(15, SECONDS));
            assertEquals("order 8 Hopper", console.poll(15, SECONDS));

            // a large push (custom protocol) then a small one (script) arrive in the order sent
            view.eval("window.__webview4j.listen('feed', f => console.info('feed ' + f.n + ' ' + f.text.length))").join();
            Thread.sleep(300);   // the page's poll, for large pushes, reaches Rust
            view.emit("feed", Map.of("n", 1, "text", "z".repeat(100_000))).join();
            view.emit("feed", Map.of("n", 2, "text", "small")).join();
            assertEquals("feed 1 100000", console.poll(15, SECONDS));
            assertEquals("feed 2 5", console.poll(15, SECONDS));

            // a window whose bridge only another origin may use: this page's calls are refused
            calls.clear();   // the "bad args" call above did reach users.find
            View locked = app.open(WindowOptions.html("<p>locked</p>").headless(true).origins("https://app.example")).join();
            locked.eval(call("locked", "window.__webview4j.invoke('users.find', {id: 1})")).join();
            assertEquals("locked failed InvokeError: this page's origin may not use the bridge", console.poll(15, SECONDS));
            // Data from that page (a call too large for IPC) is refused too, hidden by the browser as a failed fetch.
            locked.eval(call("locked data", "window.__webview4j.invoke('users.find', {blob: 'x'.repeat(70000)})")).join();
            assertEquals("locked data failed TypeError: Failed to fetch", console.poll(15, SECONDS));
            assertTrue(calls.isEmpty(), "Java never saw the refused call");

            // a command name that cannot be a subject never reaches Java
            view.eval(call("bad name", "window.__webview4j.invoke('users find')")).join();
            assertEquals("bad name failed InvokeError: 'users find' is not a valid command name", console.poll(15, SECONDS));

            // data both ways: a large argument goes over the custom protocol, a large answer is fetched
            view.eval(call("big", "window.__webview4j.invoke('users.find', {id: 9, padding: 'x'.repeat(70000)})"
                    + ".then(u => u)")).join();
            assertEquals("big ok {\"id\":9,\"name\":\"Ada\"}", console.poll(15, SECONDS));
            app.handle("files.read", call -> "y".repeat(100_000));
            view.eval("window.__webview4j.invoke('files.read').then(text => console.info('read ' + text.length))").join();
            assertEquals("read 100000", console.poll(15, SECONDS), "a small call with a large answer: settled, then fetched");
        }
    }
}
