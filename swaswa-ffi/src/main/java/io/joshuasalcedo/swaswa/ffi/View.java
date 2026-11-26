package io.joshuasalcedo.swaswa.ffi;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * One window and the page in it. Every action returns at once with a future, completed when the
 * native side has done it, or failed with a {@link WebViewException}. A view is a handle: once its
 * window closes, its actions fail.
 */
public final class View {

    private final App app;
    private final String id;

    View(App app, String id) {
        this.app = Objects.requireNonNull(app, "app");
        Objects.requireNonNull(id, "id");
        // An id is one token of every subject about this view: anything else could address another subject.
        if (!Message.validToken(id)) throw new IllegalArgumentException("'" + id + "' is not a view id");
        this.id = id;
    }

    /** The native side's name for this view, e.g. {@code wv-1}; events carry it. */
    public String id() {
        return id;
    }

    public App app() {
        return app;
    }

    // ---- the page ----

    public CompletableFuture<Void> navigate(String url) {
        return done(message("navigate").withText(url, "text/plain"));
    }

    public CompletableFuture<Void> loadHtml(String html) {
        return done(message("load-html").withText(html, "text/html"));
    }

    /**
     * Runs a script in the page; its value comes back as JSON values: {@code Map}, {@code List},
     * {@code String}, {@code Long}/{@code Double}, {@code Boolean} or null. A promise is not awaited.
     */
    public CompletableFuture<Object> eval(String script) {
        return app.request(message("eval").withText(script, "text/javascript")).thenApply(Message::json);
    }

    /** The cookies the page would send to {@code url}, HttpOnly ones included. */
    public CompletableFuture<List<Cookie>> cookies(String url) {
        return app.request(message("cookies").withText(url, "text/plain")).thenApply(View::cookieList);
    }

    /** The page's {@code localStorage}: what it keeps for its origin, across sessions. */
    public PageStorage localStorage() {
        return new PageStorage(this, "localStorage");
    }

    /** The page's {@code sessionStorage}: what it keeps for its origin while the window is open. */
    public PageStorage sessionStorage() {
        return new PageStorage(this, "sessionStorage");
    }

    /** Every cookie in the view's browser profile, whatever site, HttpOnly ones included. */
    public CompletableFuture<List<Cookie>> allCookies() {
        return app.request(message("cookies.all")).thenApply(View::cookieList);
    }

    /** Sets a session cookie (gone when the browser profile's session ends); its path is "/" unless given. */
    public CompletableFuture<Void> setCookie(Cookie cookie) {
        return setCookie(cookie, null, null);
    }

    /**
     * Sets a cookie that lives {@code maxAge} (null: a session cookie), sent {@code sameSite} (null: the browser's
     * default, Lax).
     */
    public CompletableFuture<Void> setCookie(Cookie cookie, java.time.Duration maxAge, Cookie.SameSite sameSite) {
        Map<String, Object> body = cookieBody(cookie);
        if (maxAge != null) body.put("maxAge", maxAge.toSeconds());
        if (sameSite != null) body.put("sameSite", sameSite.word);
        return done(message("cookies.set").withJson(body));
    }

    /** Deletes the cookie of this name, domain and path (as {@link #allCookies()} lists them). */
    public CompletableFuture<Void> deleteCookie(Cookie cookie) {
        return done(message("cookies.delete").withJson(cookieBody(cookie)));
    }

    private static Map<String, Object> cookieBody(Cookie cookie) {
        java.util.Objects.requireNonNull(cookie, "cookie");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("name", cookie.name());
        body.put("value", cookie.value() == null ? "" : cookie.value());
        if (cookie.domain() != null && !cookie.domain().isEmpty()) body.put("domain", cookie.domain());
        if (cookie.path() != null && !cookie.path().isEmpty()) body.put("path", cookie.path());
        body.put("secure", cookie.secure());
        body.put("httpOnly", cookie.httpOnly());
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Cookie> cookieList(Message reply) {
        List<Cookie> cookies = new ArrayList<>();
        for (Object item : (List<Object>) reply.json()) {
            Map<String, Object> c = (Map<String, Object>) item;
            cookies.add(new Cookie(Events.text(c, "name"), Events.text(c, "value"), Events.text(c, "domain"),
                    Events.text(c, "path"), Boolean.TRUE.equals(c.get("httpOnly")), Boolean.TRUE.equals(c.get("secure"))));
        }
        return List.copyOf(cookies);
    }

    // ---- the window ----

    public CompletableFuture<Void> setTitle(String title) {
        return done(message("title").withText(title, "text/plain"));
    }

    /** The inner size, in logical pixels. */
    public CompletableFuture<Void> setSize(double width, double height) {
        return done(message("size").withJson(Map.of("width", width, "height", height)));
    }

    /** The outer position, in logical pixels. */
    public CompletableFuture<Void> setPosition(double x, double y) {
        return done(message("position").withJson(Map.of("x", x, "y", y)));
    }

    /** Position and outer size in one step, in physical screen pixels: for docking over other UI. */
    public CompletableFuture<Void> setBounds(int x, int y, int width, int height) {
        return done(message("bounds").withJson(Map.of("x", x, "y", y, "width", width, "height", height)));
    }

    public CompletableFuture<Void> show()  { return done(message("show")); }
    public CompletableFuture<Void> hide()  { return done(message("hide")); }
    public CompletableFuture<Void> focus() { return done(message("focus")); }

    public CompletableFuture<Void> minimize() { return state(Map.of("minimized", true)); }
    public CompletableFuture<Void> maximize() { return state(Map.of("maximized", true)); }

    /** Back to its normal size and place: neither minimized, maximized nor fullscreen. */
    public CompletableFuture<Void> restore() {
        return state(Map.of("minimized", false, "maximized", false, "fullscreen", false));
    }

    /** Fullscreen on the display it is on, without borders; false leaves it. */
    public CompletableFuture<Void> setFullscreen(boolean on) { return state(Map.of("fullscreen", on)); }

    /** Above every other window, even when another has the focus. */
    public CompletableFuture<Void> setAlwaysOnTop(boolean on) { return state(Map.of("alwaysOnTop", on)); }

    /**
     * Light or dark for the window's frame and the page's {@code prefers-color-scheme}, at once: a page with
     * {@code @webview4j/ui} (theme "system") changes with it. {@link Theme#SYSTEM} follows the system again.
     */
    public CompletableFuture<Void> setTheme(Theme theme) {
        return done(message("theme").withText(java.util.Objects.requireNonNull(theme, "theme").key(), "text/plain"));
    }

    public CompletableFuture<Void> setResizable(boolean on) { return state(Map.of("resizable", on)); }

    // ---- the window: its frame, its buttons, its place among the others ----

    /** The largest inner size the user can resize the window to, in logical pixels. */
    public CompletableFuture<Void> setMaxSize(double width, double height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("a size is above 0: " + width + "x" + height);
        return window(Map.of("maxWidth", width, "maxHeight", height));
    }

    /** Takes the largest size off. */
    public CompletableFuture<Void> clearMaxSize() { return window(Map.of("maxWidth", 0, "maxHeight", 0)); }

    /**
     * The system's frame and title bar, on or off. Without them the app draws its own title bar (the page's
     * {@code __webview4j.window.startDrag()}, {@code minimize()}, {@code toggleMaximize()}, {@code close()}), and the
     * window is resized from the page's edges.
     */
    public CompletableFuture<Void> setDecorations(boolean on) { return window(Map.of("decorations", on)); }

    public CompletableFuture<Void> setMinimizable(boolean on) { return window(Map.of("minimizable", on)); }

    public CompletableFuture<Void> setMaximizable(boolean on) { return window(Map.of("maximizable", on)); }

    public CompletableFuture<Void> setClosable(boolean on) { return window(Map.of("closable", on)); }

    /** No taskbar entry of its own (Windows, Linux). */
    public CompletableFuture<Void> setSkipTaskbar(boolean on) { return window(Map.of("skipTaskbar", on)); }

    /** On every virtual desktop (macOS, Linux). */
    public CompletableFuture<Void> setVisibleOnAllWorkspaces(boolean on) { return window(Map.of("allWorkspaces", on)); }

    /** Left out of screenshots and screen sharing (Windows, macOS). */
    public CompletableFuture<Void> setContentProtection(boolean on) { return window(Map.of("contentProtection", on)); }

    /** Whether it takes the keyboard's focus when clicked. */
    public CompletableFuture<Void> setFocusable(boolean on) { return window(Map.of("focusable", on)); }

    /** The frameless window's shadow (Windows). */
    public CompletableFuture<Void> setShadow(boolean on) { return window(Map.of("shadow", on)); }

    /** What shows where the page is transparent: red, green, blue, alpha, each 0 to 255. */
    public CompletableFuture<Void> setBackgroundColor(int red, int green, int blue, int alpha) {
        int[] c = WindowOptions.rgba(red, green, blue, alpha);
        return window(Map.of("backgroundColor", List.of(c[0], c[1], c[2], c[3])));
    }

    /** Windows 11: the system's material behind the window. */
    public CompletableFuture<Void> setBackdrop(WindowOptions.Backdrop backdrop) {
        return window(Map.of("backdrop", backdrop.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** The taskbar's badge: a count, 0 for none (macOS's dock; elsewhere nothing yet). */
    public CompletableFuture<Void> setBadge(long count) { return window(Map.of("badge", Math.max(0, count))); }

    /** How {@link #requestAttention} asks. */
    public enum Attention {
        /** Until the app has the focus: Windows flashes the window and its taskbar button; macOS bounces the dock icon. */
        CRITICAL,
        /** Once: Windows flashes the taskbar button; macOS bounces the dock icon once. */
        INFORMATIONAL
    }

    /** Asks for the person's attention, while the app does not have the focus. */
    public CompletableFuture<Void> requestAttention(Attention attention) {
        return window(Map.of("attention", attention.name().toLowerCase(java.util.Locale.ROOT)));
    }

    /** The state of the taskbar button's progress ({@link #setProgress}). */
    public enum Progress { NONE, NORMAL, INDETERMINATE, PAUSED, ERROR }

    /**
     * The taskbar button's progress (the dock's on macOS): {@code setProgress(Progress.NORMAL, 40)};
     * {@code Progress.NONE} takes it off. {@code percent} 0 to 100, or -1 for none (an indeterminate one).
     */
    public CompletableFuture<Void> setProgress(Progress state, int percent) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("state", state.name().toLowerCase(java.util.Locale.ROOT));
        if (percent >= 0) body.put("value", Math.min(100, percent));
        return done(message("progress").withJson(body));
    }

    /** Puts the window in the middle of the monitor it is most on (or the main one). */
    public CompletableFuture<Void> center() {
        return geometry().thenCombine(monitors(), (g, monitors) -> {
            Monitor on = monitors.stream()
                    .max(java.util.Comparator.comparingDouble(m -> m.covers(g.x(), g.y(), g.width(), g.height())))
                    .filter(m -> m.covers(g.x(), g.y(), g.width(), g.height()) > 0)
                    .or(() -> monitors.stream().filter(Monitor::primary).findFirst())
                    .orElse(null);
            return on == null ? null : new double[] {on.x() + (on.width() - g.width()) / 2, on.y() + (on.height() - g.height()) / 2};
        }).thenCompose(at -> at == null ? CompletableFuture.completedFuture(null) : setPosition(at[0], at[1]));
    }

    private CompletableFuture<Void> window(Map<String, Object> body) {
        return done(message("window").withJson(body));
    }

    // ---- the page: zoom, history, print, what it keeps, its shortcuts ----

    /** The page's zoom: 1 is 100%. */
    public CompletableFuture<Void> setZoom(double zoom) {
        if (!(zoom > 0) || Double.isInfinite(zoom)) throw new IllegalArgumentException("a zoom is above 0: " + zoom);
        return done(message("zoom").withText(Double.toString(zoom), "text/plain"));
    }

    /** Loads the page again. */
    public CompletableFuture<Void> reload() { return done(message("reload")); }

    /** The page's history: back. */
    public CompletableFuture<Void> back() { return done(message("back")); }

    /** The page's history: forward. */
    public CompletableFuture<Void> forward() { return done(message("forward")); }

    /** The system's print dialog, for the page. */
    public CompletableFuture<Void> print() { return done(message("print")); }

    /** Forgets what the browser keeps for the view's profile: cookies, storage, cache. */
    public CompletableFuture<Void> clearBrowsingData() { return done(message("clear-browsing-data")); }

    /**
     * The keys the window answers itself, in place of those before (see {@link WindowOptions#shortcuts}); none takes
     * them all off. They hold for this page: a page loaded after it starts with the window's own
     * ({@link WindowOptions#shortcuts}).
     */
    public CompletableFuture<Void> setShortcuts(String... shortcuts) {
        return done(message("shortcuts").withJson(shortcutsOf(shortcuts)));
    }

    private static final java.util.regex.Pattern SHORTCUT = java.util.regex.Pattern.compile(
            "(Mod\\+)?(Ctrl\\+)?(Meta\\+)?(Alt\\+)?(Shift\\+)?([A-Z0-9]|F[1-9][0-9]?|[A-Z][A-Za-z]+)");

    /** Shortcuts checked: modifiers in their order, then one key. */
    static List<String> shortcutsOf(String... shortcuts) {
        List<String> out = new java.util.ArrayList<>();
        for (String s : shortcuts) {
            if (s == null || !SHORTCUT.matcher(s).matches())
                throw new IllegalArgumentException("a shortcut is Mod, Ctrl, Meta, Alt, Shift (in that order) and a key: " + s);
            if (!out.contains(s)) out.add(s);
        }
        return List.copyOf(out);
    }

    /**
     * The smallest inner size the user can resize the window to, in logical pixels.
     *
     * @throws IllegalArgumentException unless both are positive
     */
    public CompletableFuture<Void> setMinSize(double width, double height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("a minimum size is positive");
        return done(message("min-size").withJson(Map.of("width", width, "height", height)));
    }

    /** No minimum size any more. */
    public CompletableFuture<Void> clearMinSize() {
        return done(message("min-size").withJson(Map.of("width", 0, "height", 0)));
    }

    /**
     * Opens its developer tools (Elements, Console, Network, Sources...), as F12 does. The window must have been
     * opened with {@link WindowOptions#devtools(boolean) devtools(true)}.
     */
    public CompletableFuture<Void> openDevTools() {
        return app.request(message("devtools").withJson(Map.of("open", true))).thenApply(reply -> null);
    }

    /** Closes its developer tools. On Windows nothing: WebView2 cannot close them (the person closes them). */
    public CompletableFuture<Void> closeDevTools() {
        return app.request(message("devtools").withJson(Map.of("open", false))).thenApply(reply -> null);
    }

    /** Whether its developer tools are open; empty where the system cannot say (Windows). */
    @SuppressWarnings("unchecked")
    public CompletableFuture<java.util.Optional<Boolean>> devToolsOpen() {
        return app.request(message("devtools")).thenApply(reply -> {
            Object open = ((Map<String, Object>) reply.json()).get("open");
            return open instanceof Boolean b ? java.util.Optional.of(b) : java.util.Optional.empty();
        });
    }

    /**
     * The window's native handle, for code that talks to the system itself (JNA, a Swing window it should own): an
     * {@code HWND} on Windows, an {@code NSWindow*} on macOS. To make one of the app's windows own another, use
     * {@link WindowOptions#owner(View)} instead. Fails on Linux (not given there yet), and for an embedded view (it
     * has no window of its own).
     */
    public CompletableFuture<Long> nativeHandle() {
        return app.request(message("handle")).thenApply(reply -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> h = (Map<String, Object>) reply.json();
            return ((Number) h.get("handle")).longValue();
        });
    }

    /** Where the window is and how it is shown now: to save its place and restore it next time. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Geometry> geometry() {
        return app.request(message("geometry")).thenApply(reply -> {
            Map<String, Object> g = (Map<String, Object>) reply.json();
            return new Geometry(Events.decimal(g, "x"), Events.decimal(g, "y"), Events.decimal(g, "width"),
                    Events.decimal(g, "height"), Events.decimal(g, "scaleFactor"), flag(g, "minimized"),
                    flag(g, "maximized"), flag(g, "fullscreen"), flag(g, "focused"), flag(g, "visible"));
        });
    }

    /**
     * A picture of the window as it looks now, title bar and all: a PNG. Only the app's own windows can be captured;
     * a hidden (headless) one too, so tests and CI can take pictures without showing anything. Windows for now; other
     * systems fail saying so.
     */
    public CompletableFuture<byte[]> screenshot() {
        return screenshot(1.0);
    }

    /** ... smaller by {@code scale} (0.1 to 1). */
    public CompletableFuture<byte[]> screenshot(double scale) {
        return app.request(message("screenshot").withJson(Map.of("scale", scale))).thenApply(Message::payload);
    }

    /** A picture of the window, written to {@code file} (PNG). */
    public CompletableFuture<java.nio.file.Path> screenshot(java.nio.file.Path file) {
        return screenshot().thenApply(png -> {
            try {
                if (file.toAbsolutePath().getParent() != null) java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
                return java.nio.file.Files.write(file, png);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    /** Records the window into {@code file} until {@link Recording#stop()}: an animated GIF unless {@code options} say. */
    public CompletableFuture<Recording> record(java.nio.file.Path file, Recording.Options options) {
        try {
            if (file.toAbsolutePath().getParent() != null) java.nio.file.Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (java.io.IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        return done(message("record").withJson(options.payload(file))).thenApply(v -> new Recording(this, file));
    }

    public CompletableFuture<Recording> record(java.nio.file.Path file) {
        return record(file, Recording.Options.gif());
    }

    /** The monitors, in this window's logical pixels: where it may go. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<java.util.List<Monitor>> monitors() {
        return app.request(message("monitors")).thenApply(reply -> {
            java.util.List<Monitor> out = new java.util.ArrayList<>();
            for (Object o : (java.util.List<Object>) reply.json()) {
                Map<String, Object> m = (Map<String, Object>) o;
                out.add(new Monitor(String.valueOf(m.getOrDefault("name", "")), Events.decimal(m, "x"), Events.decimal(m, "y"),
                        Events.decimal(m, "width"), Events.decimal(m, "height"), Events.decimal(m, "scaleFactor"),
                        flag(m, "primary"), flag(m, "current")));
            }
            return java.util.List.copyOf(out);
        });
    }

    private CompletableFuture<Void> state(Map<String, Boolean> changes) {
        return done(message("state").withJson(changes));
    }

    private static boolean flag(Map<String, Object> json, String key) {
        return Boolean.TRUE.equals(json.get(key));
    }

    // ---- embedded views ----

    /**
     * Opens another page inside this view's window, as an embedded view: a website on demand, a preview, a
     * document beside the app's own UI. It sits at {@code x}, {@code y} (logical pixels from the window's top
     * left), {@code width} by {@code height}, above this page, and {@link #place moves} with the app's layout.
     * The returned view is a view like any other (navigate, eval, capture, events, close), without a window of
     * its own: what is about a window (title, size, state...) is refused. It closes with this view.
     *
     * <p>An embedded page cannot use the bridge unless {@code options} names its origins
     * ({@link WindowOptions#origins}): a website someone opens is not the app. Window options (title, frame, size
     * limits, theme) do not apply. Windows and macOS; on Linux the future fails (not supported yet).
     * <pre>{@code
     * View site = view.embed(WindowOptions.url("https://example.com").capture(Capture.API), 240, 48, 800, 600).join();
     * site.subscribe(ApiCall.class, call -> System.out.println(call.method() + " " + call.url()));
     * }</pre>
     */
    public CompletableFuture<View> embed(WindowOptions options, double x, double y, double width, double height) {
        Objects.requireNonNull(options, "options");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("an embedded view's size is positive");
        Map<String, Object> payload = options.toPayload();
        payload.put("parent", id);
        payload.put("x", x);
        payload.put("y", y);
        payload.put("width", width);
        payload.put("height", height);
        return app.open(options, payload);
    }

    /** Where this embedded view sits in its window, logical pixels from the window's top left. */
    public CompletableFuture<Void> place(double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("an embedded view's size is positive");
        return done(message("place").withJson(Map.of("x", x, "y", y, "width", width, "height", height)));
    }

    /**
     * Closes the window; a {@link Closed} event follows. With {@link WindowOptions#confirmClose
     * confirmClose}, this is how the app closes it after a {@link CloseRequested}. For an embedded view, closes
     * just it; closing a window's view closes the views embedded in it first.
     */
    public CompletableFuture<Void> close() {
        return done(message("close"));
    }

    /**
     * Sends {@code data} to the page's listeners of {@code topic}: {@code listen(topic, handler)}
     * with {@code @webview4j/api}. A topic is dot-separated words of letters, digits, {@code -} and
     * {@code _}; {@code data} is anything JSON can carry, records included.
     */
    public CompletableFuture<Void> emit(String topic, Object data) {
        Objects.requireNonNull(topic, "topic");
        if (!Message.validSubject(topic)) {
            throw new IllegalArgumentException("'" + topic + "' is not a topic: dot-separated words of letters, digits, - and _");
        }
        return done(message("emit." + topic).withJson(data));
    }

    // ---- what the page reports ----

    /** Switches one kind of report on or off, leaving the others as they are. */
    public CompletableFuture<Void> capture(Capture kind, boolean on) {
        return done(message("capture").withJson(Map.of(kind.key(), on)));
    }

    /** Exactly these reports on, the rest off. */
    public CompletableFuture<Void> capture(Set<Capture> on) {
        Set<Capture> exactly = on.isEmpty() ? EnumSet.noneOf(Capture.class) : EnumSet.copyOf(on);
        return done(message("capture").withJson(WindowOptions.switches(exactly)));
    }

    // ---- the DevTools Protocol (Windows) ----

    /**
     * Calls a method of the page's Chrome DevTools Protocol, e.g. {@code devTools("Page.captureScreenshot", Map.of())}
     * or {@code Input.dispatchMouseEvent}; its result comes back as JSON values. Windows only, for now.
     */
    public CompletableFuture<Object> devTools(String method, Map<String, ?> params) {
        Objects.requireNonNull(method, "method");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("method", method);
        body.put("params", params == null ? Map.of() : params);
        return app.request(message("cdp.call").withJson(body)).thenApply(Message::json);
    }

    /** Reports the page's DevTools Protocol {@code event} as {@link DevToolsEvent}s from now on (once per name). */
    public CompletableFuture<Void> listenDevTools(String event) {
        return done(message("cdp.listen").withText(Objects.requireNonNull(event, "event"), "text/plain"));
    }

    /** Events of one kind from this view only. */
    public <E extends WebViewEvent> Subscription subscribe(Class<E> type, Subscriber<? super E> subscriber) {
        return app.subscribe(type, id, subscriber);
    }

    /** Sending on this view's WebSockets, and their frames. */
    public WebSocketPublisher websocket() {
        return new WebSocketPublisher(this);
    }

    /** A message about this view: {@code view.<id>.<action>}. */
    Message message(String action) {
        return Message.of("view." + id + "." + action);
    }

    CompletableFuture<Void> done(Message message) {
        return app.request(message).thenApply(reply -> null);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof View view && view.app == app && view.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "View[" + id + "]";
    }
}
