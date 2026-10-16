package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.sample.UserController;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * A window opened with {@link WindowOptions#app}: its page, script and styles come from a class path
 * directory over the custom protocol, and the page uses the controllers from its own origin.
 */
@EnabledOnOs(OS.WINDOWS)
class AssetTest {

    @Test
    void pathsAreCheckedAndTyped() {
        assertTrue(Assets.safe("index.html"));
        assertTrue(Assets.safe("assets/index-B3x_9a.js"));
        for (String bad : new String[] {"", "../x", "a//b", ".env", "a/.git/x", "a\\b", "C:/x", "a%2Fb", "a b"}) {
            assertFalse(Assets.safe(bad), bad);
        }
        assertEquals("app", Assets.root("/app/"));
        assertThrows(IllegalArgumentException.class, () -> Assets.root("../etc"));
        assertEquals("text/javascript; charset=utf-8", Assets.type("assets/main.js"));
        assertEquals("font/woff2", Assets.type("x.WOFF2"));
        assertEquals("application/octet-stream", Assets.type("README"));
        assertEquals("app", WindowOptions.app("app").toPayload().get("assets"));
        assertEquals(Assets.baseUrl() + "app/index.html", WindowOptions.app("app").toPayload().get("url"));
    }

    @Test
    void unservedRootsAndMissingFilesAre404() {
        Assets assets = new Assets();
        assertEquals(404, assertThrows(HttpException.class, () -> assets.read("app", "index.html")).status());
        assets.serve("app", getClass().getClassLoader());
        assertEquals(404, assertThrows(HttpException.class, () -> assets.read("app", "nothing-here.js")).status());
        assertEquals(400, assertThrows(HttpException.class, () -> assets.read("app", "../secret")).status());
        assertThrows(IllegalStateException.class, () -> assets.serve("app", new URLClassLoader(new URL[0])),
                "one directory, one class loader");
    }

    @Test
    @Timeout(90)
    void thePageLoadsItsFilesAndCallsControllers(@TempDir Path dir) throws Exception {
        Path app = Files.createDirectories(dir.resolve("site/assets"));
        Files.writeString(dir.resolve("site/index.html"), """
                <!doctype html>
                <html><head>
                  <link rel="stylesheet" href="assets/style.css">
                  <script type="module" src="assets/main.js"></script>
                </head><body><p id="p">files</p></body></html>
                """);
        Files.writeString(app.resolve("style.css"), "#p { color: rgb(1, 2, 3); }");
        Files.writeString(app.resolve("main.js"), """
                const color = getComputedStyle(document.getElementById('p')).color;
                console.info('loaded ' + location.pathname + ' ' + color);
                const missing = await fetch('missing.js');
                console.info('missing ' + missing.status);
                const encoded = await fetch('assets/a%2F..%2Fb.js');
                console.info('encoded ' + encoded.status);
                const created = await window.__webview4j.request('POST', '/users', undefined,
                    {name: 'Ada', role: 'ADMIN', tags: [], address: null});
                console.info('api ' + created.name);
                const found = await window.__webview4j.request('GET', '/users/' + created.id);
                console.info('get ' + found.name);
                """);

        try (App webview = App.start();
             URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()})) {
            BlockingQueue<String> console = new LinkedBlockingQueue<>();
            webview.subscribe(LogRecord.class, log -> {
                if (log.source() == LogRecord.Source.PAGE) console.add(log.message());
            });
            webview.register(new UserController());
            webview.open(WindowOptions.app("site", loader).headless(true)).join();

            assertEquals("loaded /app/index.html rgb(1, 2, 3)", console.poll(30, SECONDS),
                    "index.html, its module script and its stylesheet, all from the class path");
            assertEquals("missing 404", console.poll(15, SECONDS));
            assertEquals("encoded 400", console.poll(15, SECONDS), "an encoded path never reaches Java");
            assertEquals("api Ada", console.poll(15, SECONDS), "the app's own origin may use the controllers");
            assertEquals("get Ada", console.poll(15, SECONDS),
                    "a same-origin GET, which browsers send without an Origin, is the app's own too");
        }
    }
}
