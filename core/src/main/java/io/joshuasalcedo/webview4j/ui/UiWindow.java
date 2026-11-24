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

    private final View view;
    private final Screen screen;
    private Map<String, Consumer<Object>> handlers = Map.of();

    private UiWindow(View view, Screen screen) {
        this.view = view;
        this.screen = screen;
    }

    static CompletableFuture<UiWindow> open(App app, Screen screen, UnaryOperator<WindowOptions> options) {
        Objects.requireNonNull(screen, "screen");
        Map<String, CompletableFuture<UiWindow>> windows = windows(app);
        WindowOptions page = Objects.requireNonNull(options, "options").apply(WindowOptions.html(PAGE));
        return app.open(page).thenApply(view -> {
            UiWindow window = new UiWindow(view, screen);
            windows.computeIfAbsent(view.id(), id -> new CompletableFuture<>()).complete(window);
            view.subscribe(Closed.class, closed -> windows.remove(view.id()));
            return window;
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

    /** The view the screen is shown in: to close it, move it, or embed a site in it. */
    public View view() {
        return view;
    }

    public Screen screen() {
        return screen;
    }

    /** Shows the screen again: after its state changed outside its handlers (a timer, a message, a download). */
    public CompletableFuture<Void> refresh() {
        return view.emit(TREE, tree());
    }

    /** Runs the handler the user's action names, then renders again; the page gets the new tree as the answer. */
    private Map<String, Object> handle(Invocation call) {
        Consumer<Object> handler;
        synchronized (this) {
            handler = handlers.get(call.text("handler"));
        }
        if (handler != null) {
            try {
                synchronized (screen) {
                    handler.accept(call.fields().get("value"));
                }
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "a handler of " + screen.getClass().getName() + " failed", e);
            }
        }
        return tree();
    }

    /** The screen rendered now, as the renderer reads it; its handlers are kept for the user's next action. */
    Map<String, Object> tree() {
        Map<String, Consumer<Object>> registry = new LinkedHashMap<>();
        Map<String, Object> tree;
        synchronized (screen) {
            Component<?> root = Objects.requireNonNull(screen.render(), "render() returned null");
            if (!(root instanceof Ui.Frame)) root = Ui.frame(root);
            tree = root.toTree(registry);
        }
        synchronized (this) {
            handlers = registry;
        }
        return tree;
    }
}
