package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Closed;
import io.joshuasalcedo.webview4j.Invocation;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A window showing a {@link Screen}. The page in it is webview-4j's renderer (in core's jar, with
 * {@code @webview4j/ui}): it asks Java for the screen's tree, shows it with the real components, and sends the
 * user's actions back; each runs its Java handler, and the screen renders again.
 */
public final class UiWindow {

    private static final System.Logger LOG = System.getLogger(UiWindow.class.getName());
    static final String READY = "webview4j.ui.ready";
    static final String EVENT = "webview4j.ui.event";
    static final String TREE = "webview4j.ui.tree";
    /** To the pages: server state matching {@code pattern} changed (PROTOCOL.md: webview4j.state.invalidate). */
    public static final String INVALIDATE = "webview4j.state.invalidate";

    /** The renderer's page: the kit from core's jar, and its Java-screen renderer mounted. */
    static final String PAGE = """
            <!doctype html>
            <meta charset="utf-8">
            <link rel="stylesheet" href="%1$sui.css">
            <style>html, body, #root { height: 100%%; margin: 0; }</style>
            <div id="root"></div>
            <script type="module">
              import { mountJavaUi } from "%1$sui.js";
              mountJavaUi(document.getElementById("root"));
            </script>
            """.formatted(App.UI_URL);

    /** Each app's windows by view id: a future, as the page may ask before Java has its view. */
    private static final Map<App, Map<String, CompletableFuture<UiWindow>>> APPS = new ConcurrentHashMap<>();

    /** How other exceptions become ones for people (Ui.explain): the first whose type fits. */
    private static final Map<Class<? extends Throwable>, java.util.function.Function<Throwable, UiException>> EXPLAINED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong NOTICES = new java.util.concurrent.atomic.AtomicLong();

    static {
        // A value object's exception from a handler is said as a warning, in its own words.
        io.joshuasalcedo.webview4j.ui.components.valueobject.ValueViews.install();
    }

    /**
     * Where the screen is shown: a desktop window's view, or a browser tab of a {@link WebHost}. Only what the screen
     * needs: an id, and the tree sent to it.
     */
    interface Surface {
        String id();

        CompletableFuture<Void> emit(String topic, Object data);
    }

    private final View view;
    private final Surface surface;
    private final Screen screen;
    /** The window's context: its session's values, and its own. */
    private final io.joshuasalcedo.webview4j.ui.state.Context.Scope scope;
    /** What the last render read: a change to it draws the window again. */
    private volatile io.joshuasalcedo.webview4j.ui.state.Tracking.Reader reader;
    private final java.util.concurrent.atomic.AtomicBoolean redrawQueued = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean closed;
    private Map<String, Consumer<Object>> handlers = Map.of();
    /** The last notice: what went wrong (or what to know), shown once by the page in a dialog. */
    private Map<String, Object> notice;

    private UiWindow(View view, Screen screen, io.joshuasalcedo.webview4j.ui.state.Session session) {
        this(view, new Surface() {
            @Override public String id() { return view.id(); }
            @Override public CompletableFuture<Void> emit(String topic, Object data) { return view.emit(topic, data); }
        }, screen, session);
    }

    /** A screen in a browser tab (WebHost): no native view. */
    UiWindow(Surface surface, Screen screen, io.joshuasalcedo.webview4j.ui.state.Session session) {
        this(null, surface, screen, session);
    }

    private UiWindow(View view, Surface surface, Screen screen, io.joshuasalcedo.webview4j.ui.state.Session session) {
        this.view = view;
        this.surface = surface;
        this.screen = screen;
        this.scope = new io.joshuasalcedo.webview4j.ui.state.Context.Scope(session);
    }

    static CompletableFuture<UiWindow> open(App app, Screen screen, UnaryOperator<WindowOptions> options) {
        Objects.requireNonNull(screen, "screen");
        Map<String, CompletableFuture<UiWindow>> windows = windows(app);
        WindowOptions page = Objects.requireNonNull(options, "options").apply(WindowOptions.html(PAGE));
        io.joshuasalcedo.webview4j.ui.state.Session session = session(app);
        return app.open(page).thenApply(view -> {
            UiWindow window = new UiWindow(view, screen, session);
            window.host(new DesktopHost(app, view, window));
            windows.computeIfAbsent(view.id(), id -> new CompletableFuture<>()).complete(window);
            view.subscribe(Closed.class, closed -> {
                windows.remove(view.id());
                window.closed = true;
                var r = window.reader;
                if (r != null) r.end();
            });
            // Back in front: what it shows is checked again, if stale.
            view.subscribe(io.joshuasalcedo.webview4j.Focused.class, f -> {
                if (f.focused()) window.scope.call(() -> {
                    io.joshuasalcedo.webview4j.ui.state.Tracking.focused(window.reader);
                    return null;
                });
            });
            return window;
        });
    }

    /**
     * The app's session (on the desktop, one): its state is kept in the native store in the system's place for the
     * app's data, and written out when the app stops.
     */
    public static io.joshuasalcedo.webview4j.ui.state.Session session(App app) {
        io.joshuasalcedo.webview4j.ui.state.Session session = io.joshuasalcedo.webview4j.ui.state.Session.of(app);
        session.defaultStorage(() -> io.joshuasalcedo.webview4j.NativeStore.forApp(appName()));
        if (STOPPING.add(app)) {
            // Every page of the app hears what changed: a React page's TanStack queries refresh (@webview4j/api).
            Runnable stop = session.onInvalidate(pattern -> app.views().thenAccept(views -> {
                for (var v : views) {
                    try {
                        app.view(v.id()).emit(INVALIDATE, Map.of("pattern", pattern));
                    } catch (RuntimeException e) {
                        // a view closing as it is told: nothing to refresh there
                    }
                }
            }));
            app.whenStopped().thenRun(() -> {
                STOPPING.remove(app);
                stop.run();
                session.close();
            });
        }
        return session;
    }

    private static final java.util.Set<App> STOPPING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** The app's name, for its data's folder: {@code -Dwebview4j.app=...}, else its main class's simple name. */
    static String appName() {
        String named = System.getProperty("webview4j.app");
        if (named != null && !named.isBlank()) return named;
        String command = System.getProperty("sun.java.command", "");
        String main = command.isBlank() ? "webview4j-app" : command.split(" ")[0];
        if (main.endsWith(".jar")) main = java.nio.file.Path.of(main).getFileName().toString().replaceAll("\\.jar$", "");
        return main.contains(".") ? main.substring(main.lastIndexOf('.') + 1) : main;
    }

    /** A value this window's screens (and what they build) see in their context. */
    public <T> UiWindow provide(io.joshuasalcedo.webview4j.ui.state.ContextProperty<T> property, T value) {
        scope.provide(property, value);
        return this;
    }

    public io.joshuasalcedo.webview4j.ui.state.Session session() {
        return scope.session();
    }

    /** Something the screen read changed: drawn again soon, once for a burst of changes. */
    private void redraw() {
        if (closed || !redrawQueued.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("webview4j-redraw").start(() -> {
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            redrawQueued.set(false);
            if (!closed) refresh();
        });
    }

    /** The app's windows, and its two commands the first time: the renderer's "ready" and its events. */
    private static Map<String, CompletableFuture<UiWindow>> windows(App app) {
        return APPS.computeIfAbsent(app, a -> {
            Map<String, CompletableFuture<UiWindow>> windows = new ConcurrentHashMap<>();
            a.handle(READY, call -> window(windows, call).thenApply(UiWindow::tree));
            a.handle(EVENT, call -> window(windows, call).thenApply(w -> w.handle(call)));
            a.whenStopped().thenRun(() -> APPS.remove(a));
            return windows;
        });
    }

    private static CompletableFuture<UiWindow> window(Map<String, CompletableFuture<UiWindow>> windows, Invocation call) {
        return windows.computeIfAbsent(call.view().id(), id -> new CompletableFuture<>());
    }

    /**
     * The view the screen is shown in: to close it, move it, or embed a site in it. On the web (a {@link WebHost}'s
     * browser tab) there is none: the browser owns its window.
     *
     * @throws UnsupportedOperationException in a browser tab
     */
    public View view() {
        if (view == null) throw new UnsupportedOperationException(
                "this screen is in a browser tab (a WebHost): there is no native window to move, close or embed in; check onWeb()");
        return view;
    }

    /** Whether the screen is in a browser tab of a {@link WebHost} rather than a desktop window. */
    public boolean onWeb() {
        return view == null;
    }

    /**
     * What a host's call answers, handed back the screen's way: what is chained to it ({@code thenAccept}) runs in the
     * screen's context, and the window is drawn again after; a handler that waits for it ({@code join}) simply goes on.
     */
    <T> CompletableFuture<T> answered(CompletableFuture<T> answer) {
        CompletableFuture<T> out = new CompletableFuture<>();
        answer.whenComplete((value, error) -> {
            // Not holding the screen: a handler may be waiting on this answer (join) while it holds it.
            scope.call(() -> {
                if (error != null) out.completeExceptionally(error instanceof java.util.concurrent.CompletionException c && c.getCause() != null ? c.getCause() : error);
                else out.complete(value);
                return null;
            });
            if (!closed) refresh();
        });
        return out;
    }

    /** The screen's host, in its context: {@link Host#current()}. */
    void host(Host host) {
        scope.provide(Host.CURRENT, new Answering(host));
    }

    /** A host whose answers come back the screen's way ({@link #answered}). */
    private final class Answering implements Host {
        private final Host host;

        Answering(Host host) {
            this.host = host;
        }

        @Override public boolean onWeb() { return host.onWeb(); }
        @Override public boolean supports(Feature feature) { return host.supports(feature); }
        @Override public CompletableFuture<java.util.Optional<PickedFile>> openFile(String title, String... extensions) { return answered(host.openFile(title, extensions)); }
        @Override public CompletableFuture<java.util.List<PickedFile>> openFiles(String title, String... extensions) { return answered(host.openFiles(title, extensions)); }
        @Override public CompletableFuture<Boolean> saveFile(String fileName, byte[] content) { return answered(host.saveFile(fileName, content)); }
        @Override public CompletableFuture<java.util.Optional<java.nio.file.Path>> pickFolder(String title) { return answered(host.pickFolder(title)); }
        @Override public CompletableFuture<Void> message(String title, String text, UiException.Level level) { return answered(host.message(title, text, level)); }
        @Override public CompletableFuture<Boolean> confirm(String title, String text) { return answered(host.confirm(title, text)); }
        @Override public CompletableFuture<Boolean> notify(String title, String text) { return answered(host.notify(title, text)); }
    }

    /** Its tab closed (web): what it read no longer draws it. */
    void detach() {
        closed = true;
        var r = reader;
        if (r != null) r.end();
    }

    /** Its tab came to the front (web): what it shows is checked again, if stale. */
    void focused() {
        scope.call(() -> {
            io.joshuasalcedo.webview4j.ui.state.Tracking.focused(reader);
            return null;
        });
    }

    public Screen screen() {
        return screen;
    }

    /**
     * Tells the person something in a dialog over the screen: {@code UiException.Level.INFO} for news, WARNING or
     * ERROR for problems. The same as throwing a {@link UiException} from a handler, without stopping it.
     */
    public CompletableFuture<Void> notify(String title, String message, UiException.Level level) {
        synchronized (this) {
            notice = notice(title, message, null, level);
        }
        return refresh();
    }

    static <E extends Throwable> void explain(Class<E> type, java.util.function.Function<? super E, UiException> how) {
        EXPLAINED.put(type, e -> how.apply(type.cast(e)));
    }

    private static Map<String, Object> notice(String title, String message, String details, UiException.Level level) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", NOTICES.incrementAndGet());
        n.put("title", title);
        n.put("message", message);
        if (details != null && !details.isBlank()) n.put("details", details);
        n.put("kind", level.name().toLowerCase(java.util.Locale.ROOT));
        return n;
    }

    /** What the person reads about {@code e}: its own words for a UiException (or an explained one), else a calm generic one. */
    private Map<String, Object> noticeFor(RuntimeException e) {
        UiException ui = e instanceof UiException u ? u : null;
        if (ui == null) {
            for (Map.Entry<Class<? extends Throwable>, java.util.function.Function<Throwable, UiException>> how : EXPLAINED.entrySet()) {
                if (how.getKey().isInstance(e)) {
                    ui = how.getValue().apply(e);
                    break;
                }
            }
        }
        if (ui != null) {
            Throwable cause = ui.getCause() != null ? ui.getCause() : ui == e ? null : e;
            return notice(ui.title(), ui.userMessage(), cause == null ? null : String.valueOf(cause), ui.level());
        }
        LOG.log(System.Logger.Level.ERROR, "a handler of " + screen.getClass().getName() + " failed", e);
        return notice("Something went wrong",
                "The app could not finish that. Try again; if it keeps happening, tell the people who make it.",
                e.toString(), UiException.Level.ERROR);
    }

    /** Shows the screen again: after its state changed outside its handlers (a timer, a message, a download). */
    public CompletableFuture<Void> refresh() {
        return surface.emit(TREE, tree());
    }

    /** Runs the handler the user's action names, then renders again; the page gets the new tree as the answer. */
    private Map<String, Object> handle(Invocation call) {
        return handle(call.text("handler"), call.fields().get("value"));
    }

    /** Runs the handler named {@code name} with {@code value}, then renders again: the new tree. */
    Map<String, Object> handle(String name, Object value) {
        Consumer<Object> handler;
        synchronized (this) {
            handler = handlers.get(name);
        }
        if (handler != null) {
            try {
                synchronized (screen) {
                    scope.call(() -> {
                        handler.accept(value);
                        return null;
                    });
                }
            } catch (RuntimeException e) {
                Map<String, Object> n = noticeFor(e);
                synchronized (this) {
                    notice = n;
                }
            }
        }
        return tree();
    }

    /** The screen rendered now, as the renderer reads it; its handlers are kept for the user's next action. */
    Map<String, Object> tree() {
        Map<String, Consumer<Object>> registry = new LinkedHashMap<>();
        Map<String, Object> tree;
        synchronized (screen) {
            var next = new io.joshuasalcedo.webview4j.ui.state.Tracking.Reader(this::redraw, reader);
            if (closed) next.end();
            reader = next;
            tree = scope.call(() -> io.joshuasalcedo.webview4j.ui.state.Tracking.reading(next, () -> {
                Component<?> root = Objects.requireNonNull(screen.render(), "render() returned null");
                if (!(root instanceof io.joshuasalcedo.webview4j.ui.components.Frame)) root = Ui.frame(root);
                return root.toTree(registry);
            }));
        }
        synchronized (this) {
            handlers = registry;
            if (notice != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> props = (Map<String, Object>) tree.get("p");
                props.put("notice", notice);
            }
        }
        return tree;
    }
}
