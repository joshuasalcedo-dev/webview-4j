package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * A real, hidden window driven through FFM. Windows only for now: elsewhere the loop needs the
 * main thread (macOS) or a display (Linux CI). One test, since a process gets one event loop at a time.
 */
@EnabledOnOs(OS.WINDOWS)
class EventLoopTest {

    @Test
    @Timeout(60)
    void opensAWindowDrivesItWithActionsAndQuits() throws Exception {
        assertTrue(Native.call(WindowAction.LIST, null, null).contains("not running"));
        assertFalse(Native.quit());

        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        AtomicReference<String> fromCallback = new AtomicReference<>();
        Thread loop = Thread.ofPlatform().name("webview4j-event-loop").start(() -> Native.run(event -> {
            // Calling back in from the loop thread must answer with an error, not deadlock.
            if (event.contains("\"ready\"")) fromCallback.set(Native.call(WindowAction.LIST, null, null));
            events.add(event);
        }));

        assertEquals("{\"type\":\"ready\"}", events.poll(30, SECONDS));
        assertTrue(fromCallback.get().contains("cannot be called from the event loop thread"), fromCallback.get());
        assertThrows(IllegalStateException.class, () -> Native.run(event -> {}), "a second loop must be refused");

        String opened = Native.call(WindowAction.OPEN, null, "{\"html\":\"<p id='p'>hi</p>\",\"headless\":true}");
        Matcher id = Pattern.compile("\"id\":\"(wv-\\d+)\"").matcher(opened);
        assertTrue(id.find(), opened);
        String view = id.group(1);

        assertEquals("{\"ok\":true,\"result\":2}", Native.call(WebViewAction.EVAL, view, "{\"script\":\"1+1\"}"));
        assertEquals("{\"ok\":true}", Native.call(WindowAction.SET_TITLE, view, "{\"title\":\"Renamed\"}"));
        assertTrue(Native.call(WindowAction.LIST, null, null).contains("\"title\":\"Renamed\""));
        assertTrue(Native.call(WindowAction.SET_TITLE, view, "{}").contains("invalid payload"));
        assertTrue(Native.call(WindowAction.HIDE, null, null).contains("needs a view id"));

        assertEquals("{\"ok\":true}", Native.call(WindowAction.CLOSE, view, null));
        assertTrue(Native.call(WindowAction.SHOW, view, null).contains("no webview with id"));

        assertTrue(Native.quit());
        loop.join(SECONDS.toMillis(10));
        assertFalse(loop.isAlive(), "run must return after quit");
        assertTrue(Native.call(WindowAction.LIST, null, null).contains("not running"));
    }
}
