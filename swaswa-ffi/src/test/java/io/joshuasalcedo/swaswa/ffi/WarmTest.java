package io.joshuasalcedo.swaswa.ffi;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The browser engine started while the app gets ready (app.warm): invisible to the app (no view, no events) and
 * gone once a real window is open. How much faster the first window opens is in Benchmark.
 */
@EnabledOnOs(OS.WINDOWS)
class WarmTest {

    @Test
    @Timeout(90)
    void theWarmUpIsNoViewAndLeavesNoTrace() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<Closed> closed = new LinkedBlockingQueue<>();
            app.subscribe(Closed.class, closed::add);
            Thread.sleep(1_500);   // it has started by now
            assertTrue(app.views().join().isEmpty(), "the warm-up is not one of the app's views");

            View first = app.open(WindowOptions.html("<p id='x'>first</p>").headless(true)).join();
            Object text = null;
            for (int i = 0; i < 100 && text == null; i++) {
                text = first.eval("document.getElementById('x')?.textContent ?? null").join();
                if (text == null) Thread.sleep(50);
            }
            assertEquals("first", text, "the first window's page loads");
            assertEquals(1, app.views().join().size());
            assertNull(closed.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS), "the warm-up goes without a closed event");

            // asking again once a window is open does nothing
            assertNotNull(app.request(Message.of("app.warm")).join());
            first.close().join();
            assertEquals(first.id(), closed.poll(10, SECONDS).view());
            assertTrue(app.views().join().isEmpty());

            // a window opened later still opens (the engine starts again with it)
            View again = app.open(WindowOptions.blank().headless(true)).join();
            again.eval("1").join();
        }
    }

    @Test
    @Timeout(90)
    void anAppThatOpensAWindowAtOnceIsNotSlowedByIt() throws Exception {
        // the window arrives before the warm-up's moment: it starts the engine itself, once
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>now</p>").headless(true)).join();
            view.eval("1").join();
            Thread.sleep(500);
            assertEquals(1, app.views().join().size());
        }
    }
}
