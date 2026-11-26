package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Theme;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.ui.components.layout.Pane;
import io.joshuasalcedo.webview4j.ui.components.layout.VBox;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A window made of components, as Swing's {@code JFrame}: extend it, keep its components in fields, put them in its
 * content pane, and change them in their listeners. A component changed draws the window again by itself.
 * <pre>{@code
 * public class Counter extends Window {
 *     private final Text label = new Text("Clicked 0 times");
 *     private final Button button = new Button("Click me").accent();
 *     private int count;
 *
 *     public Counter() {
 *         super("Counter");
 *         setSize(400, 300);
 *         button.addActionListener(e -> label.setText("Clicked " + ++count + " times"));
 *         add(label, button);
 *     }
 * }
 *
 * App.run(app -> new Counter().open(app));
 * }</pre>
 * The content pane is a {@link VBox} with 24 pixels inside its edge and 12 between its children, until
 * {@link #setContentPane} puts another component there (a {@code BorderPane}, a {@code Frame} with a title bar...).
 * A {@code Window} is a {@link Screen} too, so it can be shown on the web as well ({@code WebHost}).
 */
public class Window implements Screen {

    private String title;
    private double width = 800;
    private double height = 600;
    private double minWidth;
    private double minHeight;
    private boolean resizable = true;
    private boolean decorated = true;
    /** Null: the app's {@link io.joshuasalcedo.webview4j.plugin.theme.Appearance}. */
    private Theme theme;
    private volatile Component<?> contentPane = new VBox(12).padding(24);
    private volatile UiWindow shown;

    public Window() {
        this("");
    }

    public Window(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    // ---- what it shows ----

    /** What fills the window (Swing's {@code getContentPane}). */
    public Component<?> getContentPane() {
        return contentPane;
    }

    /** Puts {@code content} in the window in place of what was there; an open window draws again. */
    public Window setContentPane(Component<?> content) {
        this.contentPane = Objects.requireNonNull(content, "content");
        repaint();
        return this;
    }

    /**
     * Adds components to the content pane, at the end (Swing's {@code frame.add(component)}).
     *
     * @throws IllegalStateException when the content pane set is not a layout pane ({@code VBox}, {@code HBox},
     *                               {@code BorderPane}...): add to the component itself instead
     */
    public Window add(Component<?>... components) {
        if (!(contentPane instanceof Pane<?> pane)) {
            throw new IllegalStateException("the content pane is a " + contentPane.getClass().getSimpleName()
                    + ", not a layout pane: add to it directly, or setContentPane(new VBox(...))");
        }
        pane.add(components);
        return this;
    }

    /** Takes {@code component} out of the content pane. */
    public Window remove(Component<?> component) {
        if (contentPane instanceof Pane<?> pane) pane.remove(component);
        return this;
    }

    /** For {@link Screen}: the content pane. Components are kept between draws, so nothing is built here. */
    @Override
    public Component<?> render() {
        return contentPane;
    }

    // ---- the window itself ----

    public String getTitle() {
        return title;
    }

    /** The title in the window's bar; an open window changes it at once. */
    public Window setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
        view().ifPresent(v -> v.setTitle(title));
        return this;
    }

    /** Its size in CSS pixels; an open window changes it at once. */
    public Window setSize(double width, double height) {
        this.width = width;
        this.height = height;
        view().ifPresent(v -> v.setSize(width, height));
        return this;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    /** The smallest the user can make it (Swing's {@code setMinimumSize}). */
    public Window setMinimumSize(double width, double height) {
        this.minWidth = width;
        this.minHeight = height;
        view().ifPresent(v -> v.setMinSize(width, height));
        return this;
    }

    /** Whether the user can change its size. */
    public Window setResizable(boolean resizable) {
        this.resizable = resizable;
        view().ifPresent(v -> v.setResizable(resizable));
        return this;
    }

    public boolean isResizable() {
        return resizable;
    }

    /**
     * Without the system's frame ({@code false}; Swing's {@code setUndecorated(true)}): put a {@code Frame} with a
     * title bar in the content pane to move it. Before {@link #open} only.
     */
    public Window setDecorated(boolean decorated) {
        if (shown != null) throw new IllegalStateException("set before the window opens");
        this.decorated = decorated;
        return this;
    }

    /** Light, dark, or as the system is, for this window only (else the app's {@link io.joshuasalcedo.webview4j.plugin.theme.Appearance}). */
    public Window setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        view().ifPresent(v -> v.setTheme(theme));
        return this;
    }

    // ---- open, draw, close ----

    /** Opens it in {@code app} (Swing's {@code setVisible(true)}). */
    public CompletableFuture<UiWindow> open(App app) {
        Objects.requireNonNull(app, "app");
        if (shown != null) throw new IllegalStateException("this window is open already");
        return UiWindow.open(app, this, this::options).thenApply(window -> {
            shown = window;
            return window;
        });
    }

    /** The options it opens with: override to set more ({@code super.options(o).fileDrop(true)}). */
    protected WindowOptions options(WindowOptions options) {
        options.title(title).size(width, height).resizable(resizable).decorations(decorated)
                .theme(theme != null ? theme : io.joshuasalcedo.webview4j.plugin.theme.Appearance.current().mode());
        if (minWidth > 0 || minHeight > 0) options.minSize(minWidth, minHeight);
        return options;
    }

    /**
     * Draws it again: for changes not made through its components (a field of yours that {@code render} reads, in a
     * subclass that overrides it). Changing a component draws by itself.
     */
    public void repaint() {
        UiWindow window = shown;
        if (window != null) window.refresh();
    }

    /** Closes it (Swing's {@code dispose}). */
    public CompletableFuture<Void> dispose() {
        return view().map(View::close).orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    /** The window it is open in, once {@link #open} has opened it. */
    public Optional<UiWindow> uiWindow() {
        return Optional.ofNullable(shown);
    }

    private Optional<View> view() {
        UiWindow window = shown;
        return window == null ? Optional.empty() : Optional.ofNullable(window.view());
    }
}
