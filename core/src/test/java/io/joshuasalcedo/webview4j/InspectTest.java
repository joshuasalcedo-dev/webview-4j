package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * What a developer inspects: the page's requests (Capture.NETWORK), its cookies, its local storage, and its own
 * developer tools. In a window served from the app's own origin (a Java screen's page).
 */
@EnabledOnOs(OS.WINDOWS)
class InspectTest {

    /** A page of the app's own files, on its own origin: index.html and a JSON file to fetch. */
    private static java.net.URLClassLoader site(java.nio.file.Path dir) throws Exception {
        java.nio.file.Path app = java.nio.file.Files.createDirectories(dir.resolve("site"));
        java.nio.file.Files.writeString(app.resolve("index.html"), "<!doctype html><html><body><p>inspect me</p></body></html>");
        java.nio.file.Files.writeString(app.resolve("data.json"), "{\"orders\": [1, 2, 3], \"padding\": \"" + "x".repeat(1200) + "\"}");
        return new java.net.URLClassLoader(new java.net.URL[] {dir.toUri().toURL()});
    }

    private static View open(App app, ClassLoader loader) throws Exception {
        View view = app.open(WindowOptions.app("site", loader).headless(true).size(400, 300)).join();
        for (int i = 0; i < 200 && !String.valueOf(view.eval("document.body ? document.body.innerText : ''").join()).contains("inspect me"); i++) Thread.sleep(50);
        return view;
    }

    private static NetworkEvent next(BlockingQueue<NetworkEvent> events, NetworkEvent.Phase phase, String urlPart) throws InterruptedException {
        long until = System.nanoTime() + SECONDS.toNanos(15);
        List<String> seen = new java.util.ArrayList<>();
        while (System.nanoTime() < until) {
            NetworkEvent e = events.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (e == null) continue;
            seen.add(e.phase() + " " + e.id() + " " + e.url() + " " + e.status() + " " + e.error());
            if (e.phase() == phase && (urlPart == null || e.url() == null || e.url().contains(urlPart))) return e;
        }
        throw new AssertionError("no " + phase + " for " + urlPart + "; saw " + seen);
    }

    @Test
    @Timeout(90)
    void theNetworkIsReportedStartToEndAndFailuresSayWhy(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        try (App app = App.start(); var loader = site(dir)) {
            BlockingQueue<NetworkEvent> events = new LinkedBlockingQueue<>();
            app.subscribe(NetworkEvent.class, events::add);
            View view = open(app, loader);

            // off unless asked for
            view.eval("fetch('data.json?quiet=1').then(r => r.text())").join();
            Thread.sleep(700);
            assertTrue(events.isEmpty(), "nothing while the network is not captured: " + events);

            view.capture(Capture.NETWORK, true).join();
            view.eval("void fetch('data.json?n=1', {headers: {'X-Probe': 'yes'}}).then(r => r.text())").join();
            NetworkEvent start = next(events, NetworkEvent.Phase.START, "data.json?n=1");
            assertEquals("fetch", start.kind());
            assertEquals("GET", start.method());
            assertEquals("yes", start.requestHeaders().get("x-probe"));
            assertTrue(start.startedAt() > 0);
            NetworkEvent end = next(events, NetworkEvent.Phase.END, null);
            assertEquals(start.id(), end.id(), "a request's end has its start's id");
            assertEquals(200, end.status());
            assertEquals("application/json", end.mimeType());
            assertTrue(end.size() > 1000, "the file's size: " + end.size());
            assertTrue(end.responseBody().startsWith("{\"orders\": [1, 2, 3]"), end.responseBody());
            assertTrue(end.responseHeaders().containsKey("content-type"));

            // a request that fails says why
            view.eval("void fetch('https://nonexistent.invalid/x').catch(() => {})").join();
            NetworkEvent failing = next(events, NetworkEvent.Phase.START, "nonexistent.invalid");
            NetworkEvent error = next(events, NetworkEvent.Phase.ERROR, null);
            assertEquals(failing.id(), error.id());
            assertNotNull(error.error());
        }
    }

    @Test
    @Timeout(90)
    void cookiesAndLocalStorageAreReadAndChangedFromJava(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        try (App app = App.start(); var loader = site(dir)) {
            View view = open(app, loader);
            String origin = String.valueOf(view.eval("location.origin").join());

            // cookies: set (one HttpOnly, out of the page's reach), listed, deleted
            view.setCookie(Cookie.of("theme", "dark")).join();
            view.setCookie(new Cookie("session", "abc123", null, "/", true, true), java.time.Duration.ofHours(1), Cookie.SameSite.STRICT).join();
            List<Cookie> all = view.allCookies().join();
            Cookie session = all.stream().filter(c -> c.name().equals("session")).findFirst().orElseThrow(() -> new AssertionError("no session cookie in " + all));
            assertTrue(session.httpOnly());
            assertEquals("abc123", session.value());
            assertTrue(view.cookies(origin + "/").join().stream().anyMatch(c -> c.name().equals("theme")), "the page's own cookies");
            assertFalse(String.valueOf(view.eval("document.cookie").join()).contains("session"), "HttpOnly: the page cannot read it");
            view.deleteCookie(session).join();
            assertTrue(view.allCookies().join().stream().noneMatch(c -> c.name().equals("session")), "deleted");
            CompletionException bad = assertThrows(CompletionException.class, () -> view.setCookie(Cookie.of("bad name", "x")).join());
            assertTrue(bad.getCause().getMessage().contains("token"), bad.getCause().getMessage());
            View blank = app.open(WindowOptions.html("<p>no site</p>").headless(true)).join();
            CompletionException nowhere = assertThrows(CompletionException.class, () -> blank.setCookie(Cookie.of("a", "b")).join());
            assertTrue(nowhere.getCause().getMessage().contains("give the cookie a domain"), nowhere.getCause().getMessage());

            // local storage, from Java: what the page keeps
            PageStorage local = view.localStorage();
            local.clear().join();
            local.set("draft", "Hello \"there\"\n</script>").join();
            local.set("count", "3").join();
            assertEquals(Optional.of("Hello \"there\"\n</script>"), local.get("draft").join(), "text, never script");
            assertEquals(Map.of("draft", "Hello \"there\"\n</script>", "count", "3"), local.entries().join());
            assertEquals("3", view.eval("localStorage.getItem('count')").join(), "the page sees it");
            local.remove("count").join();
            assertEquals(Optional.empty(), local.get("count").join());
            view.sessionStorage().set("tab", "2").join();
            assertEquals(Map.of("tab", "2"), view.sessionStorage().entries().join());
        }
    }

    @Test
    @Timeout(90)
    void developerToolsOpenOnlyWhereTheWindowAllowsThem() throws Exception {
        try (App app = App.start()) {
            View closed = app.open(WindowOptions.html("<p>no tools</p>").headless(true)).join();
            CompletionException refused = assertThrows(CompletionException.class, () -> closed.openDevTools().join());
            assertTrue(refused.getCause().getMessage().contains("devtools(true)"), refused.getCause().getMessage());
            View allowed = app.open(WindowOptions.html("<p>tools</p>").headless(true).devtools(true)).join();
            // Windows cannot say whether they are open; asking does not open them
            assertEquals(Optional.empty(), allowed.devToolsOpen().join());
        }
    }
}
