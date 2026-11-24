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

    /** How other exceptions become ones for people (Ui.explain): the first whose type fits. */
    private static final Map<Class<? extends Throwable>, java.util.function.Function<Throwable, UiException>> EXPLAINED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong NOTICES = new java.util.concurrent.atomic.AtomicLong();

    private final View view;
    private final Screen screen;
    private Map<String, Consumer<Object>> handlers = Map.of();
    /** The last notice: what went wrong (or what to know), shown once by the page in a dialog. */
    private Map<String, Object> notice;

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
            Component<?> root = Objects.requireNonNull(screen.render(), "render() returned null");
            if (!(root instanceof io.joshuasalcedo.webview4j.ui.components.Frame)) root = Ui.frame(root);
            tree = root.toTree(registry);
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
