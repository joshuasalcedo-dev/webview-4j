package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.ui.state.Context;
import io.joshuasalcedo.webview4j.ui.state.ContextProperty;
import io.joshuasalcedo.webview4j.ui.state.Store;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** The web host's protocol, with a Java client in the browser's place. */
class WebHostTest {

    static final ContextProperty<String> USER = ContextProperty.of("user");
    static final Store<Integer> CLICKS = Store.of("clicks", Integer.class, () -> 0);

    static final class Counter implements Screen {
        @Override
        public Component<?> render() {
            return column(text("hello " + Context.get(USER)), text("clicks " + CLICKS.get()), button("More").onClick(() -> CLICKS.update(n -> n + 1)));
        }
    }

    /** A browser tab: the session cookie, the bridge's messages, and a map in IndexedDB's place. */
    static final class Tab implements WebSocket.Listener, AutoCloseable {
        final BlockingQueue<Map<String, Object>> pushed = new LinkedBlockingQueue<>();
        final Map<Long, CompletableFuture<Map<String, Object>>> calls = new ConcurrentHashMap<>();
        final Map<String, Object> indexedDb;
        final AtomicLong ids = new AtomicLong();
        final StringBuilder partial = new StringBuilder();
        WebSocket ws;

        Tab(URI host, String cookie, Map<String, Object> indexedDb) throws Exception {
            this.indexedDb = indexedDb;
            ws = HttpClient.newHttpClient().newWebSocketBuilder()
                    .header("Cookie", cookie)
                    .header("Origin", "http://" + host.getRawAuthority())
                    .buildAsync(host.resolve("/webview4j/socket").toString().replace("http:", "ws:").transform(URI::create), this)
                    .get(10, TimeUnit.SECONDS);
        }

        Map<String, Object> invoke(String command, Object args) throws Exception {
            long id = ids.incrementAndGet();
            CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
            calls.put(id, f);
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", id);
            m.put("invoke", command);
            m.put("args", args);
            ws.sendText(Json.write(m), true).get();
            return f.get(10, TimeUnit.SECONDS);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                Map<String, Object> m = Json.object(partial.toString());
                partial.setLength(0);
                if (m.get("id") instanceof Number n && calls.containsKey(n.longValue())) calls.remove(n.longValue()).complete(m);
                else if (m.containsKey("topic")) pushed.add(m);
                else if (m.get("storage") instanceof String op) {
                    Map<String, Object> answer = new java.util.LinkedHashMap<>();
                    answer.put("sid", m.get("sid"));
                    switch (op) {
                        case "get" -> answer.put("value", indexedDb.get(String.valueOf(m.get("key"))));
                        case "put" -> indexedDb.put(String.valueOf(m.get("key")), m.get("value"));
                        case "delete" -> answer.put("deleted", indexedDb.remove(String.valueOf(m.get("key"))) != null);
                        default -> answer.put("keys", indexedDb.keySet().stream().filter(k -> k.startsWith(String.valueOf(m.get("prefix")))).sorted().toList());
                    }
                    socket.sendText(Json.write(answer), true);
                }
            }
            socket.request(1);
            return null;
        }

        @Override
        public void close() {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        }
    }

    static String json(Object tree) {
        return Json.write(tree);
    }

    static String cookie(URI host) throws Exception {
        HttpResponse<String> page = HttpClient.newHttpClient().send(HttpRequest.newBuilder(host).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("mountJavaUi") && page.body().contains("__webview4jRemote"), "the page: the renderer and its bridge");
        return page.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
    }

    @Test
    void eachTabShowsTheScreenAndEachSessionHasItsOwnState() throws Exception {
        try (WebHost host = WebHost.of(Counter::new).title("Counter").onSession(s -> s.provide(USER, "guest")).start()) {
            String ana = cookie(host.url());
            try (Tab tab = new Tab(host.url(), ana, new ConcurrentHashMap<>())) {
                Map<String, Object> ready = tab.invoke("webview4j.ui.ready", null);
                assertTrue(json(ready.get("result")).contains("hello guest") && json(ready.get("result")).contains("clicks 0"), json(ready));

                // an event: its handler runs in the session, the answer is the new tree
                String more = handlerOf(ready.get("result"), "More");
                Map<String, Object> after = tab.invoke("webview4j.ui.event", Map.of("handler", more));
                assertTrue(json(after.get("result")).contains("clicks 1"), json(after));

                // another tab of the same session sees the same store, and is drawn again when it changes
                try (Tab second = new Tab(host.url(), ana, new ConcurrentHashMap<>())) {
                    assertTrue(json(second.invoke("webview4j.ui.ready", null).get("result")).contains("clicks 1"));
                    tab.invoke("webview4j.ui.event", Map.of("handler", handlerOf(tab.invoke("webview4j.ui.ready", null).get("result"), "More")));
                    Map<String, Object> push = second.pushed.poll(5, TimeUnit.SECONDS);
                    assertNotNull(push, "the other tab is told");
                    assertTrue(json(push.get("data")).contains("clicks 2"), json(push));
                }
                assertEquals(1, host.sessions());
            }

            // another browser: a session of its own
            String ben = cookie(host.url());
            assertNotEquals(ana, ben);
            try (Tab other = new Tab(host.url(), ben, new ConcurrentHashMap<>())) {
                assertTrue(json(other.invoke("webview4j.ui.ready", null).get("result")).contains("clicks 0"), "its own store");
            }
            assertEquals(2, host.sessions());
        }
    }

    @Test
    void anotherSitesPageAndPathsOutsideTheKitAreRefused() throws Exception {
        try (WebHost host = WebHost.of(Counter::new).start()) {
            String cookie = cookie(host.url());
            var refused = HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie", cookie).header("Origin", "https://evil.example")
                    .buildAsync(URI.create("ws://" + host.url().getRawAuthority() + "/webview4j/socket"), new WebSocket.Listener() {});
            assertThrows(Exception.class, () -> refused.get(5, TimeUnit.SECONDS), "another site's page cannot drive a session");

            HttpClient http = HttpClient.newHttpClient();
            for (String path : List.of("/ui/../module-info.class", "/ui/%2e%2e/x", "/ui/ui.css?x", "/etc/passwd")) {
                int status = http.send(HttpRequest.newBuilder(URI.create(host.url() + path.substring(1))).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                if (!path.equals("/ui/ui.css?x")) assertEquals(404, status, path);
            }
            assertEquals(200, http.send(HttpRequest.newBuilder(host.url().resolve("/ui/ui.js")).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    @Test
    void persistedStateLivesInTheBrowser() throws Exception {
        Store<String> theme = Store.of("theme", String.class, () -> "light").property(io.joshuasalcedo.webview4j.ui.state.Property.of().persist());
        final class Settings implements Screen {
            @Override
            public Component<?> render() {
                return column(text("theme " + theme.get()), button("Dark").onClick(() -> theme.set("dark")));
            }
        }
        Map<String, Object> indexedDb = new ConcurrentHashMap<>();
        try (WebHost host = WebHost.of(Settings::new).start()) {
            String cookie = cookie(host.url());
            try (Tab tab = new Tab(host.url(), cookie, indexedDb)) {
                Map<String, Object> ready = tab.invoke("webview4j.ui.ready", null);
                tab.invoke("webview4j.ui.event", Map.of("handler", handlerOf(ready.get("result"), "Dark")));
                for (int i = 0; i < 100 && indexedDb.isEmpty(); i++) Thread.sleep(20);
                assertTrue(indexedDb.containsKey("store.theme"), "written to the browser: " + indexedDb.keySet());
            }
        }
        // the next run of the server: a new session, the same browser
        try (WebHost host = WebHost.of(Settings::new).start(); Tab tab = new Tab(host.url(), cookie(host.url()), indexedDb)) {
            assertTrue(json(tab.invoke("webview4j.ui.ready", null).get("result")).contains("theme dark"), "read back from the browser");
        }
    }

    /** The handler id of the button labelled {@code label} in a tree. */
    @SuppressWarnings("unchecked")
    static String handlerOf(Object tree, String label) {
        if (tree instanceof Map<?, ?> node) {
            Map<String, Object> p = (Map<String, Object>) node.get("p");
            if (p != null && label.equals(p.get("label")) && p.get("onClick") instanceof String h) return h;
            if (node.get("c") instanceof List<?> children) for (Object c : children) {
                String h = handlerOf(c, label);
                if (h != null) return h;
            }
        }
        return null;
    }
}
