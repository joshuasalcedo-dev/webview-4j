package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * A link out of the app opens in the system's browser, never in place of the app's page. The test task sets
 * WEBVIEW4J_BROWSER=off: the decision is logged, no real browser starts.
 */
@EnabledOnOs(OS.WINDOWS)
class NavigationTest {

    static final String PAGE = """
            <p id="here">the app</p>
            <a id="out" href="https://example.com/">out</a>
            <a id="blank" href="https://example.org/" target="_blank">new window</a>
            <a id="file" href="file:///C:/Windows/win.ini">a file</a>
            """;

    private static void click(View view, String id) {
        view.eval("document.getElementById('" + id + "').click()").join();
    }

    @Test
    @Timeout(90)
    void aLinkToAnotherSiteLeavesTheAppWhereItIs() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<String> said = new LinkedBlockingQueue<>();
            app.subscribe(LogRecord.class, log -> {
                if (log.source() == LogRecord.Source.NATIVE) said.add(log.message());
            });
            View view = app.open(WindowOptions.html(PAGE).headless(true).size(600, 400)).join();
            Object here = null;
            for (int i = 0; i < 100 && here == null; i++) {
                here = view.eval("document.getElementById('here')?.textContent ?? null").join();
                if (here == null) Thread.sleep(50);
            }
            assertEquals("the app", here);

            click(view, "out");
            assertTrue(waitFor(said, "opening https://example.com/ in the system's browser"), "it goes to the browser");
            click(view, "blank");
            assertTrue(waitFor(said, "opening https://example.org/ in the system's browser"), "a new window goes there too");
            // a file: link: WebView2 blocks it before it navigates (the guard refuses it too, should it get there)
            click(view, "file");

            Thread.sleep(500);
            assertEquals("the app", view.eval("document.getElementById('here')?.textContent ?? null").join(),
                    "the app's page is still there");
            assertEquals(1, app.views().join().size(), "no other window opened");
        }
    }

    @Test
    void navigationTakesOnlyOrigins() {
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.blank().navigation("sso.example.com"));
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.blank().navigation("https://sso.example.com/login"));
        WindowOptions.blank().navigation("https://sso.example.com", "http://localhost:8081");
    }

    private static boolean waitFor(BlockingQueue<String> said, String part) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < until) {
            String line = said.poll(200, TimeUnit.MILLISECONDS);
            if (line != null && line.contains(part)) return true;
        }
        return false;
    }
}
