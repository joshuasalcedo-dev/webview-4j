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

            view.eval("window.__webview4j.listen('orders.updated', o => console.info('order ' + o.id + ' ' + o.name))").join();
            view.emit("orders.updated", new User(7, "Grace")).join();
            assertEquals("order 7 Grace", console.poll(15, SECONDS));
        }
    }
}
