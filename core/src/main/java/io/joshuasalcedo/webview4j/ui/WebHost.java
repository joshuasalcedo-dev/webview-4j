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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import io.joshuasalcedo.webview4j.ui.state.Context;
import io.joshuasalcedo.webview4j.ui.state.ContextProperty;

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
 * <p><b>Who may use it.</b> Unless told, anyone who reaches it. {@link Builder#signIn} puts a sign-in screen first
 * (tries limited per name and per address; the session's id is replaced once signed in), {@link Builder#allow}
 * decides who may use the app once signed in, and {@link Builder#trustProxyUser} takes the user from a proxy on the
 * same machine that signs people in itself. The signed-in user is {@link #account()} in every screen.
 *
 * <p><b>TLS.</b> {@link Builder#tls(Path, char[])} serves https and wss itself (cookies then Secure, HSTS sent); or put
 * a TLS proxy in front, which it trusts for {@code X-Forwarded-Proto} when the proxy is on the same machine.
 *
 * <p><b>What a desktop window has.</b> {@link Host#current()} is the same on the web: files are chosen with the
 * browser's chooser and uploaded, saved files are downloads, messages and questions are dialogs in the page,
 * notifications the browser's. A folder and the native window are not there ({@link Host#supports}).
 */
public final class WebHost implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(WebHost.class.getName());
    private static final String COOKIE = "webview4j";
    private static final String SOCKET = "/webview4j/socket";
    private static final String SIGNED_IN = "/webview4j/signed-in";
    private static final String SIGNED_OUT = "/webview4j/signed-out";
    /** Where the kit is in core's jar (App.UI_ROOT). */
    private static final String UI_ROOT = "webview4j-ui";
    private static final int MAX_HEADERS = 16 * 1024;
    private static final int MAX_MESSAGE = 16 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ---- who uses it ----

    /** Someone signed in: their name and roles. */
    public record User(String name, java.util.Set<String> roles) {
        public User {
            Objects.requireNonNull(name, "name");
            roles = java.util.Set.copyOf(roles == null ? java.util.Set.of() : roles);
        }

        public static User named(String name, String... roles) {
            return new User(name, java.util.Set.of(roles));
        }

        public boolean has(String role) {
            return roles.contains(role);
        }
    }

    /** Checks a name and password (against your users): the user, or empty when they do not match. */
    @FunctionalInterface
    public interface Authenticator {
        Optional<User> check(String name, String password);
    }

    /** The current browser session's account: who is signed in, and signing out. */
    public interface Account {
        Optional<User> user();

        /** Signs out: the session ends, its state is let go, every tab of it goes back to the start. */
        void signOut();
    }

    /** In every screen of a web host's session. */
    public static final ContextProperty<Account> ACCOUNT = ContextProperty.of("webview4j.web.account");

    /** The current session's account; empty outside a web host's screen. */
    public static Optional<Account> account() {
        return Context.find(ACCOUNT);
    }

    // ---- configuring ----

    /** A host not started yet: its screens, title, address, and what each new session gets. */
    public static final class Builder {
        private final Supplier<? extends Screen> screens;
        private String title = "App";
        private String bind = "127.0.0.1";
        private int port;
        private Consumer<Session> onSession = s -> {};
        private Duration sessionTimeout = Duration.ofMinutes(30);
        private long maxUpload = 10L * 1024 * 1024;
        private Authenticator authenticator;
        private Function<SignIn, Screen> signInScreen;
        private Predicate<User> allow = u -> true;
        private String proxyUser;
        private String proxyGroups;
        private SSLContext tls;

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

        /** The largest file a tab may upload (Host.openFile), 10 MB unless set. */
        public Builder maxUpload(long bytes) { this.maxUpload = bytes; return this; }

        /** A sign-in screen first: a name and password, checked by {@code authenticator}. */
        public Builder signIn(Authenticator authenticator) { this.authenticator = Objects.requireNonNull(authenticator, "authenticator"); return this; }

        /** ... with a sign-in screen of your own: it calls {@link SignIn#attempt} with what was typed. */
        public Builder signIn(Authenticator authenticator, Function<SignIn, Screen> screen) {
            this.signInScreen = Objects.requireNonNull(screen, "screen");
            return signIn(authenticator);
        }

        /** Who may use the app once signed in (everyone signed in, unless set); the others are told it is not for them. */
        public Builder allow(Predicate<User> who) { this.allow = Objects.requireNonNull(who, "who"); return this; }

        /**
         * The user from a proxy that signs people in itself (oauth2-proxy, a gateway): its name in {@code header}, its
         * roles (comma separated) in {@code groupsHeader} (or null). Taken only from a proxy on the same machine.
         */
        public Builder trustProxyUser(String header, String groupsHeader) {
            this.proxyUser = Objects.requireNonNull(header, "header").toLowerCase(Locale.ROOT);
            this.proxyGroups = groupsHeader == null ? null : groupsHeader.toLowerCase(Locale.ROOT);
            return this;
        }

        /** Serves https and wss itself, with this context's certificate. */
        public Builder tls(SSLContext context) { this.tls = Objects.requireNonNull(context, "context"); return this; }

        /** Serves https and wss itself, with the certificate and key in a PKCS#12 file. */
        public Builder tls(java.nio.file.Path pkcs12, char[] password) {
            try (InputStream in = java.nio.file.Files.newInputStream(pkcs12)) {
                java.security.KeyStore store = java.security.KeyStore.getInstance("PKCS12");
                store.load(in, password);
                KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keys.init(store, password);
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(keys.getKeyManagers(), null, null);
                return tls(context);
            } catch (Exception e) {
                throw new IllegalArgumentException("the certificate in " + pkcs12 + " could not be read", e);
            }
        }

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
        this.server = config.tls != null ? config.tls.getServerSocketFactory().createServerSocket() : new ServerSocket();
        if (!InetAddress.getByName(config.bind).isLoopbackAddress() && config.tls == null && config.authenticator == null && config.proxyUser == null) {
            LOG.log(System.Logger.Level.WARNING, "the web host listens on " + config.bind + " with neither sign-in nor TLS: anyone who reaches it "
                    + "can use the app, and what is sent can be read; use signIn(...) and tls(...), or a proxy that does both");
        }
        server.bind(new InetSocketAddress(InetAddress.getByName(config.bind), config.port));
        this.acceptor = Thread.ofVirtual().name("webview4j-web").start(this::accept);
        this.sweeper = Thread.ofVirtual().name("webview4j-web-sessions").start(this::sweep);
    }

    /** Where it is: {@code http://127.0.0.1:<port>/}. */
    public URI url() {
        String host = server.getInetAddress().isAnyLocalAddress() ? "127.0.0.1" : server.getInetAddress().getHostAddress();
        return URI.create((config.tls != null ? "https://" : "http://") + host + ":" + server.getLocalPort() + "/");
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
                page(socket.getOutputStream(), request, socket);
            } else if (path.equals(SIGNED_IN) || path.equals(SIGNED_OUT)) {
                swapCookie(socket.getOutputStream(), request, socket);
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
            case 303 -> "See Other";
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Status";
        };
    }

    /** The page: the kit's CSS, the bridge over the WebSocket, the renderer. A session cookie if there is none. */
    private void page(OutputStream out, Request request, Socket socket) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        if (request.cookie(COOKIE).filter(this::validId).isEmpty()) {
            headers.put("Set-Cookie", cookie(newId(), secure(request, socket)));
        }
        if (secure(request, socket)) headers.put("Strict-Transport-Security", "max-age=31536000");
        headers.put("Cache-Control", "no-store");
        // WebAssembly (egui-4j, grammar, the Excel, Word and PDF engines) needs 'wasm-unsafe-eval'; the PDF engine
        // runs in a blob: worker, and documents arrive as data: URLs and are read through blob: ones.
        headers.put("Content-Security-Policy", "default-src 'self'; img-src 'self' data: blob: https:; style-src 'self' 'unsafe-inline'; "
                + "font-src 'self' data:; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; worker-src 'self' blob:; "
                + "connect-src 'self' ws: wss: blob: data:; media-src 'self' data: blob:; frame-ancestors 'self'");
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

    // ---- cookies, sign-in ----

    /** Whether the browser reached it over TLS: its own, or a proxy's on the same machine that says so. */
    private boolean secure(Request request, Socket socket) {
        return config.tls != null || (local(socket) && "https".equalsIgnoreCase(request.header("x-forwarded-proto")));
    }

    private static boolean local(Socket socket) {
        return socket.getInetAddress() != null && socket.getInetAddress().isLoopbackAddress();
    }

    private static String cookie(String id, boolean secure) {
        return COOKIE + "=" + id + "; Path=/; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : "");
    }

    /** One-time tokens: a signed-in (or signed-out) session's new id, taken by the browser to set its cookie. */
    private record Swap(String newId, long until) {}

    private final Map<String, Swap> swaps = new ConcurrentHashMap<>();

    /** The browser comes with a token: its cookie becomes the session's new id (or a fresh one), then back to the start. */
    private void swapCookie(OutputStream out, Request request, Socket socket) throws IOException {
        String query = request.path().contains("?") ? request.path().substring(request.path().indexOf('?') + 1) : "";
        String token = query.startsWith("t=") ? query.substring(2) : "";
        Swap swap = swaps.remove(token);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Location", "/");
        headers.put("Cache-Control", "no-store");
        if (swap != null && swap.until > System.nanoTime()) headers.put("Set-Cookie", cookie(swap.newId, secure(request, socket)));
        respond(out, 303, "text/plain", new byte[0], headers);
    }

    private String token(String newId) {
        String t = newId();
        swaps.put(t, new Swap(newId, System.nanoTime() + Duration.ofMinutes(1).toNanos()));
        return t;
    }

    /** Tries per name and per address: 5 in 5 minutes. */
    private final Map<String, java.util.Deque<Long>> tries = new ConcurrentHashMap<>();

    private boolean tooMany(String who) {
        java.util.Deque<Long> t = tries.computeIfAbsent(who, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        long since = System.nanoTime() - Duration.ofMinutes(5).toNanos();
        while (!t.isEmpty() && t.peekFirst() < since) t.pollFirst();
        return t.size() >= 5;
    }

    private void failed(String who) {
        tries.computeIfAbsent(who, k -> new java.util.concurrent.ConcurrentLinkedDeque<>()).addLast(System.nanoTime());
    }

    /**
     * A sign-in attempt, for a sign-in screen of your own: what was typed, checked by the authenticator; true when it
     * matched (the tab then starts again, signed in).
     */
    public final class SignIn {
        private final WebSession web;
        private final String address;
        private String error;

        private SignIn(WebSession web, String address) {
            this.web = web;
            this.address = address;
        }

        /** What went wrong the last time, said to the person (null if nothing did). */
        public String error() {
            return error;
        }

        public boolean attempt(String name, String password) {
            String n = name == null ? "" : name.trim();
            if (tooMany("name:" + n.toLowerCase(Locale.ROOT)) || tooMany("address:" + address)) {
                error = "Too many tries. Wait a few minutes, then try again.";
                return false;
            }
            Optional<User> user = config.authenticator.check(n, password == null ? "" : password);
            if (user.isEmpty()) {
                failed("name:" + n.toLowerCase(Locale.ROOT));
                failed("address:" + address);
                try {
                    Thread.sleep(400);   // a little slower for each wrong try
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                error = "That name and password do not match.";
                return false;
            }
            error = null;
            signedIn(web, user.get());
            return true;
        }
    }

    /** Signed in: the session gets a new id (the old one is no use to anyone who saw it), every tab starts again. */
    private void signedIn(WebSession web, User user) {
        web.user = user;
        String newId = newId();
        synchronized (sessions) {
            sessions.remove(web.id, web);
            web.id = newId;
            sessions.put(newId, web);
        }
        String t = token(newId);
        for (Tab tab : web.tabs) tab.emit(NAVIGATE, Map.of("url", SIGNED_IN + "?t=" + t));
    }

    private void signOut(WebSession web) {
        sessions.remove(web.id, web);
        String t = token(newId());
        for (Tab tab : web.tabs) tab.emit(NAVIGATE, Map.of("url", SIGNED_OUT + "?t=" + t));
        web.user = null;
        web.session.close();
    }

    private static final String NAVIGATE = "webview4j.web.navigate";

    /** What a tab shows: the sign-in screen, "not for you", or the app. */
    private Screen screenFor(WebSession web, String address) {
        boolean guarded = config.authenticator != null || config.proxyUser != null;
        if (guarded && web.user == null) {
            if (config.authenticator == null) return new Message("Sign in first", "This app is signed in to through your organization's sign-in. Open it from there.", null);
            SignIn signIn = new SignIn(web, address);
            return config.signInScreen != null ? config.signInScreen.apply(signIn) : new SignInScreen(config.title, signIn);
        }
        if (web.user != null && !config.allow.test(web.user)) {
            return new Message("This app is not for you", "You are signed in as " + web.user.name() + ", who may not use " + config.title + ".",
                    () -> signOut(web));
        }
        return config.screens.get();
    }

    /** The sign-in screen unless one of your own is given. */
    private static final class SignInScreen implements Screen {
        private final String title;
        private final SignIn signIn;
        SignInScreen(String title, SignIn signIn) {
            this.title = title;
            this.signIn = signIn;
        }

        @Override
        public Component<?> render() {
            var form = new io.joshuasalcedo.webview4j.ui.components.usecase.authentication.SignInForm(title)
                    .subtitle("Sign in to go on.").username()
                    .onSubmit((name, password) -> signIn.attempt(name, password));
            if (signIn.error() != null) form.error(signIn.error());
            return Ui.column(form).align("center").padding(48);
        }
    }

    /** A screen that only says something (and offers to sign out). */
    private record Message(String title, String text, Runnable signOut) implements Screen {
        @Override
        public Component<?> render() {
            var box = Ui.column(Ui.text(title).variant(io.joshuasalcedo.webview4j.ui.components.TextVariant.TITLE),
                    Ui.text(text).tone(io.joshuasalcedo.webview4j.ui.components.Tone.SECONDARY)).gap(12).padding(32).width(420);
            if (signOut != null) box.with(Ui.button("Sign out").onClick(signOut));
            return Ui.column(box).align("center").padding(48);
        }
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
            WebSession s = new WebSession(k, Session.of("web:" + k));
            s.session.storage(s.storage);
            s.session.provide(ACCOUNT, new Account() {
                @Override public Optional<User> user() { return Optional.ofNullable(s.user); }
                @Override public void signOut() { WebHost.this.signOut(s); }
            });
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
        volatile String id;
        volatile User user;
        final Session session;
        final List<Tab> tabs = new CopyOnWriteArrayList<>();
        final BrowserStorage storage = new BrowserStorage(this);
        volatile long lastSeen = System.nanoTime();

        WebSession(String id, Session session) {
            this.id = id;
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
        if (config.proxyUser != null && local(socket)) {
            String name = request.header(config.proxyUser);
            if (name != null && !name.isBlank()) {
                String groups = config.proxyGroups == null ? null : request.header(config.proxyGroups);
                web.user = new User(name.trim(), groups == null ? java.util.Set.of()
                        : java.util.Set.copyOf(java.util.Arrays.stream(groups.split(",")).map(String::trim).filter(g -> !g.isEmpty()).toList()));
            }
        }
        String address = socket.getInetAddress() == null ? "?" : socket.getInetAddress().getHostAddress();
        try (WebSocket ws = new WebSocket(socket, in, MAX_MESSAGE)) {
            Tab tab = new Tab("tab-" + tabIds.incrementAndGet(), ws, web, address);
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

        Tab(String id, WebSocket ws, WebSession web, String address) {
            this.id = id;
            this.ws = ws;
            this.web = web;
            this.window = web.session.call(() -> new UiWindow(this, screenFor(web, address), web.session));
            window.host(new TabHost(this));
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
            return ask(request, Duration.ofSeconds(5));
        }

        Optional<Map<String, Object>> ask(Map<String, Object> request, Duration wait) {
            long sid = asks.incrementAndGet();
            CompletableFuture<Map<String, Object>> f = new CompletableFuture<>();
            asked.put(sid, f);
            Map<String, Object> m = new LinkedHashMap<>(request);
            m.put("sid", sid);
            try {
                ws.send(Json.write(m));
                return Optional.of(f.get(wait.toMillis(), TimeUnit.MILLISECONDS));
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

    /** A browser tab's {@link Host}: the browser's file chooser and downloads, dialogs in the page, its notifications. */
    private final class TabHost implements Host {
        private static final Duration PERSON = Duration.ofMinutes(30);   // as long as a person may take to answer
        private final Tab tab;

        TabHost(Tab tab) {
            this.tab = tab;
        }

        @Override
        public boolean onWeb() {
            return true;
        }

        @Override
        public boolean supports(Feature feature) {
            return switch (feature) {
                case FILE_DIALOGS, MESSAGE_BOXES, NOTIFICATIONS -> true;
                case NATIVE_WINDOW, FOLDERS, FILE_SYSTEM, TRAY -> false;
            };
        }

        private CompletableFuture<Map<String, Object>> ask(Map<String, Object> request) {
            return CompletableFuture.supplyAsync(() -> tab.ask(request, PERSON).orElse(Map.of("cancelled", true)),
                    r -> Thread.ofVirtual().start(r));
        }

        @Override
        public CompletableFuture<Optional<PickedFile>> openFile(String title, String... extensions) {
            return files(title, false, extensions).thenApply(list -> list.stream().findFirst());
        }

        @Override
        public CompletableFuture<List<PickedFile>> openFiles(String title, String... extensions) {
            return files(title, true, extensions);
        }

        private CompletableFuture<List<PickedFile>> files(String title, boolean multiple, String... extensions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("host", "openFiles");
            m.put("title", title == null ? "Choose a file" : title);
            m.put("multiple", multiple);
            m.put("accept", java.util.Arrays.stream(extensions).map(e -> "." + e.replaceFirst("^\\.", "")).toList());
            m.put("maxBytes", config.maxUpload);
            return ask(m).thenApply(answer -> {
                if (answer.get("tooBig") instanceof String name) {
                    throw new UiException("That file is too big", name + " is over " + (config.maxUpload / (1024 * 1024))
                            + " MB, the most this app takes.", UiException.Level.WARNING);
                }
                List<PickedFile> out = new ArrayList<>();
                if (answer.get("files") instanceof List<?> files) {
                    for (Object f : files) {
                        if (!(f instanceof Map<?, ?> file)) continue;
                        byte[] bytes = Base64.getDecoder().decode(String.valueOf(file.get("data")));
                        if (bytes.length > config.maxUpload) continue;
                        String name = String.valueOf(file.get("name")).replaceAll("[\\\\/]", "_");
                        out.add(new Uploaded(name, bytes));
                    }
                }
                return List.copyOf(out);
            });
        }

        @Override
        public CompletableFuture<Boolean> saveFile(String fileName, byte[] content) {
            return ask(Map.of("host", "download", "name", fileName, "data", Base64.getEncoder().encodeToString(content)))
                    .thenApply(a -> Boolean.TRUE.equals(a.get("ok")));
        }

        @Override
        public CompletableFuture<Optional<java.nio.file.Path>> pickFolder(String title) {
            return CompletableFuture.failedFuture(new UiException("Folders need the desktop app",
                    "A web page cannot open a folder on your computer. Use the desktop app, or choose the files instead.", UiException.Level.INFO));
        }

        @Override
        public CompletableFuture<Void> message(String title, String text, UiException.Level level) {
            return ask(Map.of("host", "message", "title", title, "text", text, "level", level.name().toLowerCase(Locale.ROOT))).thenApply(a -> null);
        }

        @Override
        public CompletableFuture<Boolean> confirm(String title, String text) {
            return ask(Map.of("host", "confirm", "title", title, "text", text)).thenApply(a -> Boolean.TRUE.equals(a.get("answer")));
        }

        @Override
        public CompletableFuture<Boolean> notify(String title, String text) {
            return ask(Map.of("host", "notify", "title", title, "text", text)).thenApply(a -> Boolean.TRUE.equals(a.get("system")));
        }

        @Override
        public CompletableFuture<Void> openUrl(String url) {
            if (!Host.openable(url)) return CompletableFuture.failedFuture(new IllegalArgumentException("only http, https and mailto links open"));
            return ask(Map.of("host", "open", "url", url.trim())).thenApply(a -> null);
        }
    }

    /** A file the browser uploaded: a name and its bytes, no path. */
    private record Uploaded(String name, byte[] content) implements Host.PickedFile {
        @Override public long size() { return content.length; }
        @Override public byte[] bytes() { return content.clone(); }
        @Override public Optional<java.nio.file.Path> path() { return Optional.empty(); }
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
              #webview4j-offline button { margin-left: 8px }
              .w4j-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,.35); display: grid; place-items: center; z-index: 99998 }
              .w4j-dialog { min-width: 320px; max-width: 460px; padding: 20px 22px 16px; border-radius: 10px; background: Canvas; color: CanvasText;
                font: 14px system-ui, -apple-system, "Segoe UI", sans-serif; box-shadow: 0 12px 40px rgba(0,0,0,.3) }
              .w4j-dialog h2 { margin: 0 0 8px; font-size: 16px } .w4j-dialog p { margin: 0 0 16px; white-space: pre-wrap }
              .w4j-dialog footer { display: flex; justify-content: flex-end; gap: 8px }
              .w4j-dialog button { font: inherit; padding: 6px 14px; border-radius: 6px; border: 1px solid #8884; background: ButtonFace; color: ButtonText }
              .w4j-dialog button.w4j-main { background: #0f6cbd; color: white; border-color: #0f6cbd }
              .w4j-toast { position: fixed; right: 16px; bottom: 16px; max-width: 360px; padding: 12px 14px; border-radius: 8px; background: #323130;
                color: white; font: 14px system-ui, sans-serif; z-index: 99999; box-shadow: 0 6px 20px rgba(0,0,0,.3) }
              .w4j-toast strong { display: block; margin-bottom: 2px }</style>
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
              // What a screen asks of its host (Host.current()): dialogs in the page, the file chooser, downloads, notifications.
              function dialog(title, text, buttons, extra) {
                return new Promise((resolve) => {
                  const back = document.createElement("div");
                  back.className = "w4j-backdrop";
                  const box = document.createElement("div");
                  box.className = "w4j-dialog";
                  box.setAttribute("role", "dialog");
                  box.setAttribute("aria-modal", "true");
                  const h = document.createElement("h2"); h.textContent = title;
                  const p = document.createElement("p"); p.textContent = text || "";
                  const footer = document.createElement("footer");
                  box.append(h, p);
                  if (extra) box.append(extra);
                  box.append(footer);
                  const done = (value) => { back.remove(); resolve(value); };
                  for (const b of buttons) {
                    const el = document.createElement("button");
                    el.textContent = b.label;
                    if (b.main) el.className = "w4j-main";
                    el.onclick = () => (b.run ? b.run(done) : done(b.value));
                    footer.append(el);
                  }
                  back.addEventListener("keydown", (e) => { if (e.key === "Escape") done(buttons.find((b) => b.cancel)?.value ?? false) });
                  back.append(box);
                  document.body.append(back);
                  footer.querySelector(".w4j-main, button")?.focus();
                });
              }
              function toBase64(buffer) {
                const bytes = new Uint8Array(buffer);
                let s = "";
                for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
                return btoa(s);
              }
              function chooseFiles(m) {
                return new Promise((resolve) => {
                  const input = document.createElement("input");
                  input.type = "file";
                  input.multiple = !!m.multiple;
                  if (m.accept && m.accept.length) input.accept = m.accept.join(",");
                  input.style.display = "none";
                  document.body.append(input);
                  let settled = false;
                  const finish = (v) => { if (!settled) { settled = true; input.remove(); resolve(v) } };
                  input.addEventListener("change", async () => {
                    const files = Array.from(input.files || []);
                    const big = files.find((f) => f.size > m.maxBytes);
                    if (big) return finish({ tooBig: big.name });
                    finish({ files: await Promise.all(files.map(async (f) => ({ name: f.name, size: f.size, data: toBase64(await f.arrayBuffer()) }))) });
                  });
                  input.addEventListener("cancel", () => finish({ cancelled: true }));
                  // The browser opens its chooser only right after a click: at once if the person just clicked, else after one more.
                  if (navigator.userActivation && navigator.userActivation.isActive) input.click();
                  else dialog(m.title, "", [
                    { label: "Choose…", main: true, run: (close) => { close(); input.click() } },
                    { label: "Cancel", cancel: true, run: (close) => { close(); finish({ cancelled: true }) } },
                  ]);
                });
              }
              function toast(title, text) {
                const t = document.createElement("div");
                t.className = "w4j-toast";
                t.setAttribute("role", "status");
                const s = document.createElement("strong"); s.textContent = title;
                t.append(s, document.createTextNode(text || ""));
                document.body.append(t);
                setTimeout(() => t.remove(), 6000);
              }
              async function host(m) {
                switch (m.host) {
                  case "openFiles": return chooseFiles(m);
                  case "download": {
                    const bytes = Uint8Array.from(atob(m.data), (c) => c.charCodeAt(0));
                    const url = URL.createObjectURL(new Blob([bytes]));
                    const a = document.createElement("a");
                    a.href = url; a.download = m.name; document.body.append(a); a.click(); a.remove();
                    setTimeout(() => URL.revokeObjectURL(url), 30000);
                    return { ok: true };
                  }
                  case "open": {
                    if (/^(https?:|mailto:)/i.test(m.url)) window.open(m.url, "_blank", "noopener,noreferrer");
                    return { ok: true };
                  }
                  case "message": await dialog(m.title, m.text, [{ label: "OK", main: true, value: true, cancel: true }]); return { ok: true };
                  case "confirm": return { answer: await dialog(m.title, m.text, [{ label: "OK", main: true, value: true }, { label: "Cancel", value: false, cancel: true }]) };
                  case "notify": {
                    if ("Notification" in window) {
                      if (Notification.permission === "default") { try { await Notification.requestPermission() } catch (e) {} }
                      if (Notification.permission === "granted") { new Notification(m.title, { body: m.text }); return { system: true } }
                    }
                    toast(m.title, m.text);
                    return { system: false };
                  }
                }
                return {};
              }
              listen("webview4j.web.navigate", (d) => { location.href = d.url });
              ws.onmessage = async (e) => {
                const m = JSON.parse(e.data);
                if (m.id !== undefined && pending.has(m.id)) {
                  const p = pending.get(m.id);
                  pending.delete(m.id);
                  if (m.error !== undefined) p.reject(Object.assign(new Error(m.error), { name: "InvokeError" }));
                  else p.resolve(m.result);
                } else if (m.topic) {
                  for (const h of Array.from(listeners.get(m.topic) || [])) { try { h(m.data) } catch (err) { console.error(err) } }
                } else if (m.storage || m.host) {
                  let answer;
                  try { answer = m.storage ? await storage(m) : await host(m) } catch (err) { answer = { error: String(err) } }
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
