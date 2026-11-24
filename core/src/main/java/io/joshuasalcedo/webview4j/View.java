package io.joshuasalcedo.webview4j;

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
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Cookie>> cookies(String url) {
        return app.request(message("cookies").withText(url, "text/plain")).thenApply(reply -> {
            List<Cookie> cookies = new ArrayList<>();
            for (Object item : (List<Object>) reply.json()) {
                Map<String, Object> c = (Map<String, Object>) item;
                cookies.add(new Cookie(Events.text(c, "name"), Events.text(c, "value"), Events.text(c, "domain"),
                        Events.text(c, "path"), Boolean.TRUE.equals(c.get("httpOnly")), Boolean.TRUE.equals(c.get("secure"))));
            }
            return List.copyOf(cookies);
        });
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
