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

    /** Closes the window; a {@link Closed} event follows. */
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
