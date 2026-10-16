package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.WebSocketFrame.Direction;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The whole API against a real, hidden window. Windows only for now, like EventLoopTest. */
@EnabledOnOs(OS.WINDOWS)
class AppTest {

    private static final String PAGE = "<button id='go'>Go</button>";

    @Test
    @Timeout(90)
    void actionsAreFuturesAndPageReportsReachSubscribers() throws Exception {
        try (App app = App.start(); TestWebSocketServer server = new TestWebSocketServer()) {
            BlockingQueue<WebViewEvent> events = new LinkedBlockingQueue<>();
            app.subscribe(WebViewEvent.class, events::add);
            BlockingQueue<Thread> subscriberThreads = new LinkedBlockingQueue<>();
            app.subscribe(LogRecord.class, log -> subscriberThreads.add(Thread.currentThread()));

            View view = app.open(WindowOptions.html(PAGE).headless(true)
                    .capture(Capture.CLICKS, Capture.WEBSOCKET, Capture.CONSOLE)).join();
            BlockingQueue<Click> clicks = new LinkedBlockingQueue<>();
            view.subscribe(Click.class, clicks::add);

            // eval: a future with the value as JSON values
            assertEquals(2L, view.eval("1 + 1").join());
            assertEquals(List.of("a", true), view.eval("['a', true]").join());

            // every action is metered: its frames both ways and its round trip, by its name
            Metrics metrics = app.metrics();
            assertTrue(metrics.framesOut() >= 3 && metrics.framesIn() >= 3, metrics.toString());
            assertTrue(metrics.bytesOut() > 0 && metrics.bytesIn() > 0, metrics.toString());
            Metrics.Timing evals = metrics.byAction().stream()
                    .filter(t -> t.name().equals("view.eval")).findFirst().orElseThrow();
            assertEquals(2, evals.count());
            assertTrue(evals.averageMillis() > 0 && evals.maxMillis() >= evals.p95Millis(), evals.toString());
            assertEquals(1, metrics.byAction().stream()
                    .filter(t -> t.name().equals("app.open")).findFirst().orElseThrow().count());

            // the console arrives as a page log record, on a subscriber thread, never the loop's
            view.eval("console.warn('careful', {n: 1})").join();
            LogRecord warning = next(events, LogRecord.class, log -> log.source() == LogRecord.Source.PAGE);
            assertEquals(LogRecord.Level.WARN, warning.level());
            assertEquals("careful {\"n\":1}", warning.message());
            assertEquals(view.id(), warning.view());
            Thread thread = subscriberThreads.poll(10, SECONDS);
            assertTrue(thread.isVirtual(), thread.toString());

            // clicks, to the app-wide and to the view's own subscribers
            view.eval("document.getElementById('go').click()").join();
            assertEquals("#go", next(events, Click.class, c -> true).selector());
            Click click = clicks.poll(10, SECONDS);
            assertNotNull(click);
            assertEquals("button", click.tag());
            assertEquals("Go", click.text());

            // the page's WebSocket: open, a frame in, a frame sent from Java, its echo as out
            view.eval("window.socket = new WebSocket('" + server.url() + "')").join();
            server.awaitConnection();
            WebSocketFrame open = next(events, WebSocketFrame.class, f -> f.direction() == Direction.OPEN);
            assertEquals(server.url(), open.url());
            server.send("hello from the server");
            WebSocketFrame in = next(events, WebSocketFrame.class, f -> f.direction() == Direction.IN);
            assertEquals("hello from the server", in.data());
            assertEquals(open.socket(), in.socket());

            view.websocket().send(open.socket(), "hello from Java").join();
            assertEquals("hello from Java", server.received.poll(10, SECONDS));
            assertEquals("hello from Java", next(events, WebSocketFrame.class, f -> f.direction() == Direction.OUT).data());
            CompletionException noSocket = assertThrows(CompletionException.class, () -> view.websocket().send(99, "x").join());
            assertTrue(noSocket.getCause().getMessage().contains("no open socket 99"), noSocket.getCause().getMessage());

            // switching a report off, then on again
            view.capture(Capture.CLICKS, false).join();
            view.eval("document.getElementById('go').click(); console.log('after the click')").join();
            next(events, LogRecord.class, log -> "after the click".equals(log.message()));
            assertNull(clicks.poll(), "clicks were switched off");

            // failures are failed futures
            CompletionException bad = assertThrows(CompletionException.class, () -> app.view("wv-404").show().join());
            assertInstanceOf(WebViewException.class, bad.getCause());
            assertTrue(bad.getCause().getMessage().contains("no view 'wv-404'"), bad.getCause().getMessage());

            // names that would change a message's subject never get that far
            assertThrows(IllegalArgumentException.class, () -> app.view("wv-1.close"));
            assertThrows(IllegalArgumentException.class, () -> view.emit("orders..updated", 1));
            assertThrows(IllegalArgumentException.class, () -> app.handle("users find", call -> null));

            assertEquals(1, app.views().join().size());
            view.close().join();
            assertEquals(view.id(), next(events, Closed.class, c -> true).view());
            assertTrue(app.views().join().isEmpty());
        }
    }

    @Test
    @Timeout(60)
    void aStoppedAppFailsWhatIsAskedOfIt() throws Exception {
        App app = App.start();
        app.close();
        assertTrue(app.whenStopped().isDone());
        CompletionException stopped = assertThrows(CompletionException.class,
                () -> app.open(WindowOptions.blank()).join());
        assertTrue(stopped.getCause().getMessage().contains("not running"), stopped.getCause().getMessage());
    }

    /** The next event of a type that matches, skipping the rest. */
    private static <E extends WebViewEvent> E next(BlockingQueue<WebViewEvent> events, Class<E> type, Predicate<E> matches)
            throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            WebViewEvent event = events.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (type.isInstance(event) && matches.test(type.cast(event))) return type.cast(event);
        }
        throw new AssertionError("no " + type.getSimpleName() + " came");
    }
}
