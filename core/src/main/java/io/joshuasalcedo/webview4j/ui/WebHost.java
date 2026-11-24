package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.ui.state.Session;
import io.joshuasalcedo.webview4j.ui.state.StateStorage;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The same screens on the web: a server that shows a {@link Screen} in every browser tab, the way a desktop window
 * shows it. The page is webview-4j's renderer, talking to Java over a WebSocket instead of the native bridge; each
 * browser session (a cookie) is a {@link Session} of its own, with its context, stores and server state, and keeps
 * what it persists in that browser's IndexedDB. No dependency: plain sockets and virtual threads.
 * <pre>{@code
 * WebHost host = WebHost.of(OfficeScreen::new).title("Office").port(8080).bind("0.0.0.0")
 *         .onSession(session -> session.provide(USER, guest))
 *         .start();
 * System.out.println(host.url());
 * }</pre>
 * Put a TLS proxy in front of it on the internet (https and wss). What only a desktop window has (its native view:
 * moving it, file dialogs, embedding a site) is not there: {@link UiWindow#onWeb()} says which a screen is in.
 */
public final class WebHost implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(WebHost.class.getName());
    private static final String COOKIE = "webview4j";
    private static final String SOCKET = "/webview4j/socket";
    /** Where the kit is in core's jar (App.UI_ROOT). */
    private static final String UI_ROOT = "webview4j-ui";
    private static final int MAX_HEADERS = 16 * 1024;
    private static final int MAX_MESSAGE = 16 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ---- configuring ----

    /** A host not started yet: its screens, title, address, and what each new session gets. */
    public static final class Builder {
        private final Supplier<? extends Screen> screens;
        private String title = "App";
        private String bind = "127.0.0.1";
        private int port;
        private Consumer<Session> onSession = s -> {};
        private Duration sessionTimeout = Duration.ofMinutes(30);

        private Builder(Supplier<? extends Screen> screens) {
            this.screens = Objects.requireNonNull(screens, "screens");
        }

        /** The page's title. */
        public Builder title(String title) { this.title = Objects.requireNonNull(title, "title"); return this; }
        /** The port; 0 (the default) for any free one. */
        public Builder port(int port) { this.port = port; return this; }
        /** The address it listens on: 127.0.0.1 unless set ("0.0.0.0" for every address of the machine). */
        public Builder bind(String address) { this.bind = Objects.requireNonNull(address, "address"); return this; }
        /** Run for each new browser session: what it provides (a user, services) every screen of it sees. */
        public Builder onSession(Consumer<Session> setup) { this.onSession = Objects.requireNonNull(setup, "setup"); return this; }
        /** How long a session lives with no tab open (30 minutes unless set): then its state is let go. */
        public Builder sessionTimeout(Duration timeout) { this.sessionTimeout = Objects.requireNonNull(timeout, "timeout"); return this; }

        public WebHost start() {
            try {
                return new WebHost(this);
            } catch (IOException e) {
                throw new IllegalStateException("the web host could not listen on " + bind + ":" + port, e);
            }
        }
    }

    /** A host showing a new screen from {@code screens} in each tab. */
    public static Builder of(Supplier<? extends Screen> screens) {
        return new Builder(screens);
    }

    // ---- running ----

    private final Builder config;
    private final ServerSocket server;
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong tabIds = new AtomicLong();
    private final Thread acceptor;
    private final Thread sweeper;
    private volatile boolean closed;

    private WebHost(Builder config) throws IOException {
        this.config = config;
        this.server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByName(config.bind), config.port));
        this.acceptor = Thread.ofVirtual().name("webview4j-web").start(this::accept);
        this.sweeper = Thread.ofVirtual().name("webview4j-web-sessions").start(this::sweep);
    }

    /** Where it is: {@code http://127.0.0.1:<port>/}. */
    public URI url() {
        String host = server.getInetAddress().isAnyLocalAddress() ? "127.0.0.1" : server.getInetAddress().getHostAddress();
        return URI.create("http://" + host + ":" + server.getLocalPort() + "/");
    }

    public int port() {
        return server.getLocalPort();
    }

    /** The browser sessions now. */
    public int sessions() {
        return sessions.size();
    }

    /** The tabs open now, over all sessions. */
    public int tabs() {
        return sessions.values().stream().mapToInt(s -> s.tabs.size()).sum();
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException e) {
            // closing
        }
        sweeper.interrupt();
        for (WebSession s : sessions.values()) {
            for (Tab t : s.tabs) t.close();
            s.session.close();
        }
        sessions.clear();
    }

    private void accept() {
        while (!closed) {
            try {
                Socket socket = server.accept();
                Thread.ofVirtual().name("webview4j-web-connection").start(() -> serve(socket));
            } catch (IOException e) {
                if (!closed) LOG.log(System.Logger.Level.WARNING, "the web host could not take a connection", e);
            }
        }
    }

    private void sweep() {
        while (!closed) {
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                return;
            }
            long now = System.nanoTime();
            sessions.entrySet().removeIf(e -> {
                WebSession s = e.getValue();
                if (!s.tabs.isEmpty() || now - s.lastSeen < config.sessionTimeout.toNanos()) return false;
                s.session.close();
                return true;
            });
        }
    }

    // ---- HTTP ----

    private record Request(String method, String path, Map<String, String> headers) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        Optional<String> cookie(String name) {
            String all = header("cookie");
            if (all == null) return Optional.empty();
            for (String part : all.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return Optional.of(kv[1]);
            }
            return Optional.empty();
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            socket.setSoTimeout((int) Duration.ofSeconds(30).toMillis());
            InputStream in = new BufferedInputStream(socket.getInputStream());
            Request request = read(in);
            if (request == null) return;
            String path = request.path().split("\\?", 2)[0];
            if (path.equals(SOCKET) && "websocket".equalsIgnoreCase(request.header("upgrade"))) {
                socket.setSoTimeout(0);
                upgrade(socket, in, request);
            } else if (!request.method().equals("GET") && !request.method().equals("HEAD")) {
                respond(socket.getOutputStream(), 405, "text/plain", "Only GET".getBytes(StandardCharsets.UTF_8), Map.of());
            } else if (path.equals("/") || path.isEmpty()) {
                page(socket.getOutputStream(), request);
            } else if (path.startsWith("/ui/")) {
                asset(socket.getOutputStream(), path.substring("/ui/".length()));
            } else {
                respond(socket.getOutputStream(), 404, "text/plain", "Not here".getBytes(StandardCharsets.UTF_8), Map.of());
            }
        } catch (IOException e) {
            // the browser went away
        }
    }

    /** The request line and headers, or null if the connection closed first. */
    private static Request read(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) return null;
            head.write(b);
            if (head.size() > MAX_HEADERS) throw new IOException("headers too long");
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        String[] lines = head.toString(StandardCharsets.ISO_8859_1).split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) throw new IOException("not HTTP");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        return new Request(first[0], first[1], headers);
    }

    private static void respond(OutputStream out, int status, String type, byte[] body, Map<String, String> headers) throws IOException {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        head.append("Content-Type: ").append(type).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Connection: close\r\n");
        head.append("X-Content-Type-Options: nosniff\r\n");
        headers.forEach((k, v) -> head.append(k).append(": ").append(v).append("\r\n"));
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String reason(int status) {
        return switch (status) {
            case 101 -> "Switching Protocols";
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Status";
        };
    }

    /** The page: the kit's CSS, the bridge over the WebSocket, the renderer. A session cookie if there is none. */
    private void page(OutputStream out, Request request) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        if (request.cookie(COOKIE).filter(this::validId).isEmpty()) {
            headers.put("Set-Cookie", COOKIE + "=" + newId() + "; Path=/; HttpOnly; SameSite=Lax");
        }
        headers.put("Cache-Control", "no-store");
        headers.put("Content-Security-Policy", "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; "
                + "script-src 'self' 'unsafe-inline'; connect-src 'self' ws: wss:; frame-ancestors 'self'");
        String html = PAGE.replace("%TITLE%", escape(config.title));
        respond(out, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), headers);
    }

    /** One of the kit's files, from core's jar; nothing outside its folder. */
    private static void asset(OutputStream out, String path) throws IOException {
        if (!path.matches("[A-Za-z0-9._/-]+") || path.contains("..") || path.startsWith("/")) {
            respond(out, 404, "text/plain", "Not here".getBytes(StandardCharsets.UTF_8), Map.of());
            return;
        }
        try (InputStream file = WebHost.class.getModule().getResourceAsStream(UI_ROOT + "/" + path)) {
            InputStream f = file != null ? file : WebHost.class.getClassLoader().getResourceAsStream(UI_ROOT + "/" + path);
            if (f == null) {
                respond(out, 404, "text/plain", "Not here".getBytes(StandardCharsets.UTF_8), Map.of());
                return;
            }
            try (f) {
                respond(out, 200, type(path), f.readAllBytes(), Map.of("Cache-Control", "public, max-age=3600"));
            }
        }
    }

    private static String type(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".json") || p.endsWith(".map")) return "application/json";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".woff")) return "font/woff";
        return "application/octet-stream";
    }

    // ---- sessions and tabs ----

    private String newId() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private boolean validId(String id) {
        return id.matches("[0-9a-f]{32}");
    }

    private WebSession session(String id) {
        return sessions.computeIfAbsent(id, k -> {
            WebSession s = new WebSession(Session.of("web:" + k));
            s.session.storage(s.storage);
            // A React page's TanStack queries, and this session's tabs, hear what changed.
            s.session.onInvalidate(pattern -> {
                for (Tab t : s.tabs) t.emit(UiWindow.INVALIDATE, Map.of("pattern", pattern));
            });
            s.session.call(() -> {
                config.onSession.accept(s.session);
                return null;
            });
            return s;
        });
    }

    /** One browser session: its state, its tabs, and its storage in the browser. */
    private static final class WebSession {
        final Session session;
        final List<Tab> tabs = new CopyOnWriteArrayList<>();
        final BrowserStorage storage = new BrowserStorage(this);
        volatile long lastSeen = System.nanoTime();

        WebSession(Session session) {
            this.session = session;
        }
    }

    private void upgrade(Socket socket, InputStream in, Request request) throws IOException {
        OutputStream out = socket.getOutputStream();
        // Only the host's own pages: another site's page must not drive a session with the person's cookie.
        String origin = request.header("origin");
        String host = request.header("host");
        if (origin == null || host == null || !URI.create(origin).getRawAuthority().equalsIgnoreCase(host)) {
            respond(out, 403, "text/plain", "Another site's page".getBytes(StandardCharsets.UTF_8), Map.of());
            return;
        }
        Optional<String> id = request.cookie(COOKIE).filter(this::validId);
        String key = request.header("sec-websocket-key");
        if (id.isEmpty() || key == null) {
            respond(out, 400, "text/plain", "No session".getBytes(StandardCharsets.UTF_8), Map.of());
            return;
        }
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                + WebSocket.accept(key) + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        WebSession web = session(id.get());
        try (WebSocket ws = new WebSocket(socket, in, MAX_MESSAGE)) {
            Tab tab = new Tab("tab-" + tabIds.incrementAndGet(), ws, web);
            web.tabs.add(tab);
            web.storage.connected(tab);
            try {
                tab.run();
            } finally {
                web.tabs.remove(tab);
                web.lastSeen = System.nanoTime();
                tab.close();
            }
        }
    }

    /** One browser tab: a screen of its own, shown over its WebSocket. */
    private final class Tab implements UiWindow.Surface {
        private final String id;
        private final WebSocket ws;
        private final WebSession web;
        private final UiWindow window;
        /** Calls run one at a time, in order, off the socket's reader (which must stay free for storage answers). */
        private final ExecutorService calls = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("webview4j-tab").factory());
        private final Map<Long, CompletableFuture<Map<String, Object>>> asked = new ConcurrentHashMap<>();
        private final AtomicLong asks = new AtomicLong();

        Tab(String id, WebSocket ws, WebSession web) {
            this.id = id;
            this.ws = ws;
            this.web = web;
            this.window = web.session.call(() -> new UiWindow(this, config.screens.get(), web.session));
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public CompletableFuture<Void> emit(String topic, Object data) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("topic", topic);
            m.put("data", data);
            try {
                ws.send(Json.write(m));
                return CompletableFuture.completedFuture(null);
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        void run() throws IOException {
            for (String text; (text = ws.read()) != null; ) {
                Map<String, Object> m;
                try {
                    m = Json.object(text);
                } catch (RuntimeException e) {
                    continue;
                }
                if (m.get("sid") instanceof Number n) {   // the browser's answer to a storage request
                    CompletableFuture<Map<String, Object>> f = asked.remove(n.longValue());
                    if (f != null) f.complete(m);
                } else if (m.get("invoke") instanceof String command) {
                    Object callId = m.get("id");
                    Object args = m.get("args");
                    calls.execute(() -> answer(callId, command, args));
                } else if ("focused".equals(m.get("event"))) {
                    calls.execute(window::focused);
                }
            }
        }

        private void answer(Object callId, String command, Object args) {
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", callId);
            try {
                Object result = switch (command) {
                    case UiWindow.READY -> window.tree();
                    case UiWindow.EVENT -> {
                        Map<?, ?> a = args instanceof Map<?, ?> map ? map : Map.of();
                        yield window.handle(String.valueOf(a.get("handler")), a.get("value"));
                    }
                    default -> throw new IllegalArgumentException("no command " + command + " on the web");
                };
                reply.put("result", result);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "a web tab's " + command + " failed", e);
                reply.put("error", "The app could not do that.");
            }
            try {
                ws.send(Json.write(reply));
            } catch (IOException e) {
                // the tab went away
            }
        }

        /** Asks the browser's IndexedDB; empty if it does not answer in time. */
        Optional<Map<String, Object>> ask(Map<String, Object> request) {
            long sid = asks.incrementAndGet();
            CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
            asked.put(sid, f);
            Map<String, Object> m = new LinkedHashMap<>(request);
            m.put("sid", sid);
            try {
                ws.send(Json.write(m));
                return Optional.of(f.get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
                return Optional.empty();
            } finally {
                asked.remove(sid);
            }
        }

        void close() {
            window.detach();
            calls.shutdownNow();
            ws.close();
        }
    }

    /**
     * A web session's {@link StateStorage}: the browser's IndexedDB, asked over any of its open tabs (they share one
     * per site). Writes with no tab open wait for the next one.
     */
    private static final class BrowserStorage implements StateStorage {
        private final WebSession web;
        private final Map<String, byte[]> waiting = new LinkedHashMap<>();
        private static final byte[] DELETE = new byte[0];

        BrowserStorage(WebSession web) {
            this.web = web;
        }

        private Optional<Tab> tab() {
            List<Tab> tabs = new ArrayList<>(web.tabs);
            return tabs.isEmpty() ? Optional.empty() : Optional.of(tabs.getLast());
        }

        /** A tab opened: what waited is written. */
        void connected(Tab tab) {
            Map<String, byte[]> now;
            synchronized (waiting) {
                now = new LinkedHashMap<>(waiting);
                waiting.clear();
            }
            Thread.ofVirtual().start(() -> now.forEach((k, v) -> {
                if (v == DELETE) tab.ask(Map.of("storage", "delete", "key", k));
                else tab.ask(Map.of("storage", "put", "key", k, "value", Base64.getEncoder().encodeToString(v)));
            }));
        }

        @Override
        public void put(String key, byte[] value) {
            Optional<Tab> tab = tab();
            if (tab.isEmpty() || tab.get().ask(Map.of("storage", "put", "key", key, "value", Base64.getEncoder().encodeToString(value))).isEmpty()) {
                synchronized (waiting) {
                    waiting.put(key, value.clone());
                }
            }
        }

        @Override
        public Optional<byte[]> get(String key) {
            synchronized (waiting) {
                byte[] w = waiting.get(key);
                if (w != null) return w == DELETE ? Optional.empty() : Optional.of(w.clone());
            }
            return tab().flatMap(t -> t.ask(Map.of("storage", "get", "key", key)))
                    .map(m -> m.get("value"))
                    .filter(v -> v instanceof String)
                    .map(v -> Base64.getDecoder().decode((String) v));
        }

        @Override
        public boolean delete(String key) {
            Optional<Tab> tab = tab();
            Optional<Map<String, Object>> answer = tab.flatMap(t -> t.ask(Map.of("storage", "delete", "key", key)));
            if (answer.isEmpty()) {
                synchronized (waiting) {
                    waiting.put(key, DELETE);
                }
                return false;
            }
            return Boolean.TRUE.equals(answer.get().get("deleted"));
        }

        @Override
        public List<String> keys(String prefix) {
            return tab().flatMap(t -> t.ask(Map.of("storage", "keys", "prefix", prefix)))
                    .map(m -> m.get("keys"))
                    .filter(v -> v instanceof List<?>)
                    .map(v -> ((List<?>) v).stream().map(String::valueOf).toList())
                    .orElse(List.of());
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** The page: the bridge over the WebSocket (for the renderer), IndexedDB for the session's state, the renderer. */
    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%TITLE%</title>
            <link rel="stylesheet" href="/ui/ui.css">
            <style>html, body, #root { height: 100%; margin: 0; }
              #webview4j-offline { position: fixed; inset: auto 16px 16px auto; padding: 10px 14px; border-radius: 8px;
                background: #323130; color: white; font: 14px system-ui, sans-serif; display: none; z-index: 99999 }
              #webview4j-offline button { margin-left: 8px }</style>
            <div id="root"></div>
            <div id="webview4j-offline" role="alert">The connection to the app was lost.<button onclick="location.reload()">Reload</button></div>
            <script>
            (() => {
              const ws = new WebSocket((location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/webview4j/socket");
              const open = new Promise((resolve) => ws.addEventListener("open", resolve, { once: true }));
              const pending = new Map();
              const listeners = new Map();
              let next = 1;
              function invoke(command, args) {
                return open.then(() => new Promise((resolve, reject) => {
                  const id = next++;
                  pending.set(id, { resolve, reject });
                  ws.send(JSON.stringify({ id, invoke: command, args: args === undefined ? null : args }));
                }));
              }
              function listen(topic, handler) {
                let set = listeners.get(topic);
                if (!set) listeners.set(topic, set = new Set());
                const entry = (data) => handler(data);
                set.add(entry);
                return () => set.delete(entry);
              }
              // The session's state, in this browser: one IndexedDB for the site, values as base64 text.
              const db = new Promise((resolve, reject) => {
                const req = indexedDB.open("webview4j", 1);
                req.onupgradeneeded = () => req.result.createObjectStore("state");
                req.onsuccess = () => resolve(req.result);
                req.onerror = () => reject(req.error);
              });
              function store(mode, work) {
                return db.then((d) => new Promise((resolve, reject) => {
                  const tx = d.transaction("state", mode);
                  const r = work(tx.objectStore("state"));
                  tx.oncomplete = () => resolve(r && "result" in r ? r.result : undefined);
                  tx.onerror = () => reject(tx.error);
                }));
              }
              async function storage(m) {
                switch (m.storage) {
                  case "get": { const v = await store("readonly", (s) => s.get(m.key)); return { value: v === undefined ? null : v }; }
                  case "put": await store("readwrite", (s) => s.put(m.value, m.key)); return { ok: true };
                  case "delete": {
                    const had = await store("readonly", (s) => s.count(m.key));
                    await store("readwrite", (s) => s.delete(m.key));
                    return { deleted: had > 0 };
                  }
                  case "keys": {
                    const all = await store("readonly", (s) => s.getAllKeys());
                    return { keys: all.filter((k) => typeof k === "string" && k.startsWith(m.prefix)).sort() };
                  }
                }
                return {};
              }
              ws.onmessage = async (e) => {
                const m = JSON.parse(e.data);
                if (m.id !== undefined && pending.has(m.id)) {
                  const p = pending.get(m.id);
                  pending.delete(m.id);
                  if (m.error !== undefined) p.reject(Object.assign(new Error(m.error), { name: "InvokeError" }));
                  else p.resolve(m.result);
                } else if (m.topic) {
                  for (const h of Array.from(listeners.get(m.topic) || [])) { try { h(m.data) } catch (err) { console.error(err) } }
                } else if (m.storage) {
                  let answer;
                  try { answer = await storage(m) } catch (err) { answer = { error: String(err) } }
                  ws.send(JSON.stringify({ sid: m.sid, ...answer }));
                }
              };
              ws.onclose = () => {
                document.getElementById("webview4j-offline").style.display = "block";
                for (const p of pending.values()) p.reject(Object.assign(new Error("The connection to the app was lost."), { name: "InvokeError" }));
                pending.clear();
              };
              const focused = () => { if (ws.readyState === 1) ws.send(JSON.stringify({ event: "focused" })) };
              window.addEventListener("focus", focused);
              document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") focused() });
              window.__webview4jRemote = Object.freeze({ invoke, listen });
            })();
            </script>
            <script type="module">
              import { mountJavaUi } from "/ui/ui.js";
              mountJavaUi(document.getElementById("root"));
            </script>
            </html>
            """;
}
