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
        this.id = Objects.requireNonNull(id, "id");
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
        return done(WebViewAction.NAVIGATE, Map.of("url", url));
    }

    public CompletableFuture<Void> loadHtml(String html) {
        return done(WebViewAction.LOAD_HTML, Map.of("html", html));
    }

    /**
     * Runs a script in the page; its value comes back as JSON values: {@code Map}, {@code List},
     * {@code String}, {@code Long}/{@code Double}, {@code Boolean} or null. A promise is not awaited.
     */
    public CompletableFuture<Object> eval(String script) {
        return app.submit(WebViewAction.EVAL, id, Map.of("script", script)).thenApply(reply -> reply.get("result"));
    }

    /** The cookies the page would send to {@code url}, HttpOnly ones included. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<Cookie>> cookies(String url) {
        return app.submit(WebViewAction.COOKIES, id, Map.of("url", url)).thenApply(reply -> {
            List<Cookie> cookies = new ArrayList<>();
            for (Object item : (List<Object>) reply.getOrDefault("cookies", List.of())) {
                Map<String, Object> c = (Map<String, Object>) item;
                cookies.add(new Cookie(Events.text(c, "name"), Events.text(c, "value"), Events.text(c, "domain"),
                        Events.text(c, "path"), Boolean.TRUE.equals(c.get("httpOnly")), Boolean.TRUE.equals(c.get("secure"))));
            }
            return List.copyOf(cookies);
        });
    }

    // ---- the window ----

    public CompletableFuture<Void> setTitle(String title) {
        return done(WindowAction.SET_TITLE, Map.of("title", title));
    }

    /** The inner size, in logical pixels. */
    public CompletableFuture<Void> setSize(double width, double height) {
        return done(WindowAction.SET_SIZE, Map.of("width", width, "height", height));
    }

    /** The outer position, in logical pixels. */
    public CompletableFuture<Void> setPosition(double x, double y) {
        return done(WindowAction.SET_POSITION, Map.of("x", x, "y", y));
    }

    /** Position and outer size in one step, in physical screen pixels: for docking over other UI. */
    public CompletableFuture<Void> setBounds(int x, int y, int width, int height) {
        return done(WindowAction.SET_BOUNDS, Map.of("x", x, "y", y, "width", width, "height", height));
    }

    public CompletableFuture<Void> show()  { return done(WindowAction.SHOW, null); }
    public CompletableFuture<Void> hide()  { return done(WindowAction.HIDE, null); }
    public CompletableFuture<Void> focus() { return done(WindowAction.FOCUS, null); }

    /** Closes the window; a {@link Closed} event follows. */
    public CompletableFuture<Void> close() {
        return done(WindowAction.CLOSE, null);
    }

    // ---- what the page reports ----

    /** Switches one kind of report on or off, leaving the others as they are. */
    public CompletableFuture<Void> capture(Capture kind, boolean on) {
        return done(InspectorAction.CAPTURE, Map.of(kind.key(), on));
    }

    /** Exactly these reports on, the rest off. */
    public CompletableFuture<Void> capture(Set<Capture> on) {
        return done(InspectorAction.CAPTURE, WindowOptions.switches(on.isEmpty() ? EnumSet.noneOf(Capture.class) : EnumSet.copyOf(on)));
    }

    /** Events of one kind from this view only. */
    public <E extends WebViewEvent> Subscription subscribe(Class<E> type, Subscriber<? super E> subscriber) {
        return app.subscribe(type, id, subscriber);
    }

    /** Sending on this view's WebSockets, and their frames. */
    public WebSocketPublisher websocket() {
        return new WebSocketPublisher(this);
    }

    private CompletableFuture<Void> done(Action action, Map<String, ?> payload) {
        return app.submit(action, id, payload).thenApply(reply -> null);
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
