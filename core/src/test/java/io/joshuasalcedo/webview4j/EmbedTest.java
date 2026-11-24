package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Embedded views: a page inside another view's window. Windows only, like AppTest. */
@EnabledOnOs(OS.WINDOWS)
class EmbedTest {

    @Test
    @Timeout(90)
    void aPageOpensInsideAWindowWithoutTheBridgeAndClosesWithIt() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<Closed> closed = new LinkedBlockingQueue<>();
            app.subscribe(Closed.class, closed::add);
            app.handle("secret", call -> "the app's own data");
            View window = app.open(WindowOptions.html("<p>host</p>").headless(true).size(800, 600)).join();

            View site = window.embed(WindowOptions.html("<p id='x'>embedded</p>"), 100, 50, 400, 300).join();
            assertNotEquals(window.id(), site.id());
            assertEquals("embedded", until(site, "document.getElementById('x')?.textContent ?? null"));
            // in the screen's pixels (CSS pixels scale with the system's text size)
            String width = "innerWidth * devicePixelRatio";
            assertEquals(400, ((Number) site.eval(width).join()).doubleValue(), 2,
                    "it is as wide as it was placed");

            // someone else's page gets no bridge unless Java names its origin
            site.eval("void window.__webview4j.invoke('secret').then(v => window.answer = 'GOT ' + v, e => window.answer = 'REFUSED ' + e.status)").join();
            Object answer = null;
            for (int i = 0; i < 50 && answer == null; i++) {
                Thread.sleep(100);
                answer = site.eval("window.answer ?? null").join();
            }
            assertEquals("REFUSED 403", answer);

            // it moves with the layout; what is about a window is refused
            site.place(10, 10, 600, 200).join();
            assertEquals(600, ((Number) site.eval(width).join()).doubleValue(), 2);
            CompletionException title = assertThrows(CompletionException.class, () -> site.setTitle("x").join());
            assertTrue(title.getCause().getMessage().contains("is embedded"), title.getCause().getMessage());
            CompletionException place = assertThrows(CompletionException.class, () -> window.place(0, 0, 10, 10).join());
            assertTrue(place.getCause().getMessage().contains("is not embedded"), place.getCause().getMessage());
            CompletionException nested = assertThrows(CompletionException.class,
                    () -> site.embed(WindowOptions.blank(), 0, 0, 10, 10).join());
            assertTrue(nested.getCause().getMessage().contains("embedded itself"), nested.getCause().getMessage());
            site.hide().join();
            site.show().join();

            // the views are listed, and closing the window closes what is embedded in it first
            assertEquals(2, app.views().join().size());
            window.close().join();
            assertEquals(site.id(), closed.poll(10, SECONDS).view());
            assertEquals(window.id(), closed.poll(10, SECONDS).view());
            assertTrue(app.views().join().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> window.embed(WindowOptions.blank(), 0, 0, 0, 10));
        }
    }

    /** A script's value once it is not null: the page loads after the view exists. */
    private static Object until(View view, String script) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Object value = view.eval(script).join();
            if (value != null) return value;
            Thread.sleep(100);
        }
        return null;
    }
}
