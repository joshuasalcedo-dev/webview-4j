package io.joshuasalcedo.webview4j.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Closed;
import io.joshuasalcedo.webview4j.PageLoad;
import io.joshuasalcedo.webview4j.Subscription;
import io.joshuasalcedo.webview4j.TitleChanged;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.Theme;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/**
 * A modern native webview as a JavaFX node: WebView2, the engine of Microsoft Edge (Chromium, kept up to date by
 * Windows), not JavaFX's own WebView and its old WebKit. Put it in any layout; it follows its place, size and
 * visibility.
 *
 * <pre>{@code
 * App app = App.start();                                      // once, beside JavaFX
 * WebViewPane web = WebViewPane.url(app, "https://example.com");
 * root.setCenter(web);
 * web.dom().setText("h1", "Changed from JavaFX");
 * }</pre>
 *
 * <p>Two ways to draw it ({@link #setRendering}):
 * <ul>
 *   <li>{@link Rendering#NODE} (the default): a JavaFX node like any other. The page's frames are drawn in the scene
 *       (from the engine's DevTools screencast) and the mouse and keys go to it from JavaFX, so a scroll pane clips
 *       it, other nodes and popups go over it, and effects, fades and tab switches work. Its native window stays
 *       under the node, all but invisible, so the engine keeps drawing and its own pop-ups open where they should.
 *   <li>{@link Rendering#NATIVE}: the page's own window, kept exactly over this node: the engine draws it straight to
 *       the screen (the fastest: video, games), but it is above the scene, so no JavaFX node can cover it.
 * </ul>
 * Windows only, for now.
 *
 * <p>Everything here is called on the FX thread; the futures complete on other threads.
 */
public class WebViewPane extends Region {

    /** How the page is drawn: in the scene, or as its own window over it. */
    public enum Rendering { NODE, NATIVE }

    private static volatile Rendering defaultRendering = Rendering.NODE;

    /** How panes made from now on draw their page, unless they say: {@link Rendering#NODE} unless set. */
    public static void setDefaultRendering(Rendering rendering) {
        defaultRendering = Objects.requireNonNull(rendering, "rendering");
    }

    private final App app;
    private final WindowOptions options;
    /** Opens the page with the options that dock it here. */
    private final Function<UnaryOperator<WindowOptions>, CompletableFuture<View>> opener;
    private final ReadOnlyStringWrapper location = new ReadOnlyStringWrapper(this, "location", "");
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", "");
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(this, "loading", false);
    private final List<Consumer<View>> onOpen = new ArrayList<>();
    private final AnimationTimer follower = new AnimationTimer() {
        @Override
        public void handle(long now) {
            follow();
        }
    };

    /** The page now, and the next one when the stage shows again after it was hidden. */
    private CompletableFuture<View> view = new CompletableFuture<>();
    private View opened;
    private Stage stage;
    private boolean opening;
    private boolean closed;
    private boolean shown;
    private Placement placed;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final javafx.event.EventHandler<WindowEvent> onHidden = event -> drop();
    /** The page has drawn its first page: until then JavaFX shows a spinner, not a blank white window. */
    private boolean painted;
    private final ProgressIndicator spinner = new ProgressIndicator();
    private Theme theme = Theme.SYSTEM;
    private final BooleanProperty scrollbars = new SimpleBooleanProperty(this, "scrollbars", true);
    private Rendering rendering = defaultRendering;
    /** The page drawn in the scene ({@link Rendering#NODE}). */
    private final NodeRendering drawn;

    /** Opens what {@code options} say (a url, html, the app's files...) once this node is in a showing stage. */
    public WebViewPane(App app, WindowOptions options) {
        this(app, options, null);
    }

    /**
     * For a page another API opens (a {@link io.joshuasalcedo.webview4j.ui.Screen}: see {@link ScreenPane}):
     * {@code opener} opens it with the window options it is given, after applying {@code dock} to them, the options
     * that put it over this node.
     */
    protected WebViewPane(App app, Function<UnaryOperator<WindowOptions>, CompletableFuture<View>> opener) {
        this(app, WindowOptions.blank(), Objects.requireNonNull(opener, "opener"));
    }

    private WebViewPane(App app, WindowOptions options, Function<UnaryOperator<WindowOptions>, CompletableFuture<View>> opener) {
        this.app = Objects.requireNonNull(app, "app");
        this.options = Objects.requireNonNull(options, "options");
        this.opener = opener != null ? opener : dock -> app.open(dock.apply(this.options));
        if (!NativeWindows.supported()) throw new UnsupportedOperationException("webview-4j-fx docks webviews on Windows only, for now");
        getStyleClass().add("webview-pane");
        setMinSize(0, 0);
        setPrefSize(800, 600);
        spinner.setMaxSize(36, 36);
        drawn = new NodeRendering(this, () -> spinner.setVisible(false));
        getChildren().addAll(drawn.image(), spinner);
        paintBackground();
        sceneProperty().addListener((property, before, now) -> {
            if (now != null && !closed) follower.start();
            else follower.stop();
            follow();
        });
        scrollbars.addListener((p, before, on) -> {
            if (opened != null) applyScrollbars(opened);
        });
    }

    @Override
    protected void layoutChildren() {
        double size = 36;
        drawn.layout(getWidth(), getHeight());
        spinner.resizeRelocate((getWidth() - size) / 2, (getHeight() - size) / 2, size, size);
    }

    // ---- how it looks ----

    /**
     * Light or dark for the page ({@code prefers-color-scheme}, so a page with a dark style switches to it) and for
     * the space it fills while loading. {@link Theme#SYSTEM} follows the system.
     */
    public void setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        options.theme(theme);
        paintBackground();
        if (opened != null) opened.setTheme(theme);
    }

    /**
     * Draws the page in the scene ({@link Rendering#NODE}) or as its own window over it ({@link Rendering#NATIVE}).
     * Changing it once the page is open opens it again.
     */
    public void setRendering(Rendering rendering) {
        Objects.requireNonNull(rendering, "rendering");
        if (rendering == this.rendering) return;
        this.rendering = rendering;
        drawn.image().setVisible(rendering == Rendering.NODE);
        drop();
        follow();
    }

    public Rendering getRendering() {
        return rendering;
    }

    public Theme getTheme() {
        return theme;
    }

    /** False hides the page's scrollbars (it still scrolls: wheel, keys, touch); on every page it goes to. */
    public BooleanProperty scrollbarsProperty() { return scrollbars; }
    public boolean isScrollbars() { return scrollbars.get(); }
    public void setScrollbars(boolean on) { scrollbars.set(on); }

    private void paintBackground() {
        boolean dark = theme == Theme.DARK;
        setBackground(new Background(new BackgroundFill(dark ? Color.web("#18181b") : Color.WHITE, null, null)));
    }

    private void applyScrollbars(View view) {
        boolean hide = !scrollbars.get();
        view.eval("(() => { let s = document.getElementById('__webview4j_scrollbars');"
                + " if (" + hide + ") { if (!s) { s = document.createElement('style'); s.id = '__webview4j_scrollbars';"
                + " s.textContent = 'html, body { scrollbar-width: none !important } ::-webkit-scrollbar { display: none !important }';"
                + " (document.head || document.documentElement).append(s); } } else if (s) { s.remove(); } return " + hide + "; })()");
    }

    public static WebViewPane url(App app, String url) {
        return new WebViewPane(app, WindowOptions.url(url));
    }

    public static WebViewPane html(App app, String html) {
        return new WebViewPane(app, WindowOptions.html(html));
    }

    // ---- the page ----

    /** The page's view once it is open: for anything this class does not do ({@link View#subscribe}, cookies...). */
    public CompletableFuture<View> view() {
        return view;
    }

    /** Runs {@code action} with the view whenever one opens: now if one is open, and again after a reopen. */
    public void whenOpen(Consumer<View> action) {
        onOpen.add(Objects.requireNonNull(action, "action"));
        if (opened != null) action.accept(opened);
    }

    public CompletableFuture<Void> navigate(String url) {
        return view.thenCompose(v -> v.navigate(url));
    }

    public CompletableFuture<Void> loadHtml(String html) {
        return view.thenCompose(v -> v.loadHtml(html));
    }

    /** Runs a script in the page; its value comes back as JSON values (see {@link View#eval}). */
    public CompletableFuture<Object> eval(String script) {
        return view.thenCompose(v -> v.eval(script));
    }

    public CompletableFuture<Object> reload() { return eval("location.reload(); null"); }
    public CompletableFuture<Object> back()   { return eval("history.back(); null"); }
    public CompletableFuture<Object> forward() { return eval("history.forward(); null"); }

    /** Changes the page's elements from Java: text, styles, classes, attributes, new and removed elements. */
    public Dom dom() {
        return new Dom(this::eval);
    }

    /** The page's address, after each navigation. */
    public ReadOnlyStringProperty locationProperty() { return location.getReadOnlyProperty(); }
    public String getLocation() { return location.get(); }

    /** The page's {@code document.title}. */
    public ReadOnlyStringProperty titleProperty() { return title.getReadOnlyProperty(); }
    public String getTitle() { return title.get(); }

    /** True from a navigation's start until its page has loaded. */
    public ReadOnlyBooleanProperty loadingProperty() { return loading.getReadOnlyProperty(); }
    public boolean isLoading() { return loading.get(); }

    /** Closes the page for good: the node stays, empty. */
    public void close() {
        closed = true;
        follower.stop();
        drop();
        drawn.close();
        view.completeExceptionally(new IllegalStateException("this WebViewPane is closed"));
    }

    // ---- keeping the page over the node ----

    private void follow() {
        if (closed) return;
        Scene scene = getScene();
        Window window = scene == null ? null : scene.getWindow();
        if (window == null) {
            // Out of the scene, maybe for a moment (a layout moving it): hidden, not closed.
            if (shown) {
                opened.hide();
                shown = false;
            }
            return;
        }
        if (window != stage) {
            drop();
            if (stage != null) stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
            stage = window instanceof Stage s ? s : null;
            if (stage != null) stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
        }
        if (stage == null || !stage.isShowing()) return;
        if (opened == null) {
            if (!opening) open();
            return;
        }
        boolean inScene = rendering == Rendering.NODE;
        if (inScene) drawn.flush();
        // In the scene, the (invisible) window covers the whole node, cut or not: the page draws at its full size.
        Placement place = !visible() ? null : inScene ? fullPlacement(scene) : placement(scene);
        if (place == null) {
            if (shown) {
                opened.hide();
                shown = false;
            }
            return;
        }
        if (!place.equals(placed)) {
            opened.setBounds(place.x(), place.y(), place.width(), place.height());
            placed = place;
        }
        if (!shown && (painted || inScene)) {
            opened.show();
            shown = true;
            if (!inScene) spinner.setVisible(false);
        }
    }

    private void open() {
        // Not before the node is laid out: until then it has no size, and no place on the screen (NaN).
        Bounds screen = localToScreen(getLayoutBounds());
        if (screen == null || !(getWidth() > 0 && getHeight() > 0)
                || !Double.isFinite(screen.getMinX()) || !Double.isFinite(screen.getMinY())) return;
        long owner = NativeWindows.handleOf(stage);
        if (owner == 0) return; // the stage's window is not there yet: the next frame tries again
        Stage openedFor = stage;
        double width = getWidth(), height = getHeight(), x = screen.getMinX(), y = screen.getMinY();
        Theme chosen = theme;
        boolean inScene = rendering == Rendering.NODE;
        UnaryOperator<WindowOptions> dock = o -> {
            o.ownerWindow(owner).decorations(false).resizable(false).headless(true)
                    .size(width, height).position(x, y).theme(chosen);
            return inScene ? o.offscreen() : o;
        };
        CompletableFuture<View> pending;
        try {
            pending = opener.apply(dock);
        } catch (RuntimeException e) {
            view.completeExceptionally(e);
            return;
        }
        opening = true;
        pending.whenComplete((v, error) -> Platform.runLater(() -> {
            opening = false;
            if (error != null) {
                view.completeExceptionally(error);
                return;
            }
            if (closed || stage != openedFor) {
                v.close();
                return;
            }
            opened = v;
            placed = null;
            shown = false;
            painted = false;
            spinner.setVisible(true);
            subscriptions.add(v.subscribe(PageLoad.class, e -> Platform.runLater(() -> {
                if (e.url() != null) location.set(e.url());
                loading.set(e.state() == PageLoad.State.STARTED);
                if (e.state() == PageLoad.State.FINISHED && opened == v) {
                    painted = true;
                    if (!scrollbars.get()) applyScrollbars(v);
                }
            })));
            // A page that is slow to finish (a big site) shows anyway after a moment, loading in front of the user.
            PauseTransition patience = new PauseTransition(Duration.seconds(2.5));
            patience.setOnFinished(e -> {
                if (opened == v) painted = true;
            });
            patience.play();
            subscriptions.add(v.subscribe(TitleChanged.class, e -> Platform.runLater(() -> title.set(e.title() == null ? "" : e.title()))));
            subscriptions.add(v.subscribe(Closed.class, e -> Platform.runLater(() -> {
                if (opened == v) forget();
            })));
            if (rendering == Rendering.NODE) drawn.start(v);
            view.complete(v);
            for (Consumer<View> action : List.copyOf(onOpen)) action.accept(v);
            follow();
        }));
    }

    /** Closes the page's window (the stage hid, or this node moved to another stage); it opens again when shown. */
    private void drop() {
        if (opened != null) {
            View v = opened;
            forget();
            v.close();
        }
    }

    private void forget() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        drawn.stop();
        opened = null;
        placed = null;
        shown = false;
        if (view.isDone() && !closed) view = new CompletableFuture<>();
    }

    private boolean visible() {
        if (stage.isIconified() || getWidth() <= 0 || getHeight() <= 0) return false;
        for (Node node = this; node != null; node = node.getParent()) {
            if (!node.isVisible()) return false;
        }
        return true;
    }

    /** The whole node on the screen, uncut. */
    private Placement fullPlacement(Scene scene) {
        Bounds node = localToScreen(getLayoutBounds());
        if (node == null) return null;
        Window window = scene.getWindow();
        return Placement.of(node, node, window.getOutputScaleX(), window.getOutputScaleY());
    }

    /** The node on the screen, cut to its scene and to every clip above it. */
    private Placement placement(Scene scene) {
        Bounds node = localToScreen(getLayoutBounds());
        if (node == null) return null;
        Window window = scene.getWindow();
        Bounds seen = new BoundingBox(window.getX() + scene.getX(), window.getY() + scene.getY(), scene.getWidth(), scene.getHeight());
        for (Parent parent = getParent(); parent != null; parent = parent.getParent()) {
            Node clip = parent.getClip();
            if (clip != null) {
                Bounds cut = parent.localToScreen(clip.getBoundsInParent());
                if (cut != null) seen = Placement.intersect(seen, cut);
            }
        }
        return Placement.of(node, seen, window.getOutputScaleX(), window.getOutputScaleY());
    }
}
