package io.joshuasalcedo.webview4j.fx;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;

/**
 * A screen written in Java with webview-4j's components ({@link Ui}: buttons, fields, cards, lists, money...), shown
 * in a JavaFX layout. No HTML, CSS or JavaScript: its fields are its state, and it draws again after each of its
 * handlers, or on {@link #refresh()} when JavaFX changed them.
 *
 * <pre>{@code
 * class Counter implements Screen {
 *     int count;
 *     public Component<?> render() {
 *         return column(title("Clicked " + count + " times"), button("Click me").accent().onClick(() -> count++));
 *     }
 * }
 * Counter counter = new Counter();
 * ScreenPane pane = new ScreenPane(app, counter);
 * javafxButton.setOnAction(e -> { counter.count = 0; pane.refresh(); });
 * }</pre>
 */
public class ScreenPane extends WebViewPane {

    private final Screen screen;
    private volatile CompletableFuture<UiWindow> window = new CompletableFuture<>();

    public ScreenPane(App app, Screen screen) {
        this(app, screen, new CompletableFuture[1]);
    }

    @SuppressWarnings("unchecked")
    private ScreenPane(App app, Screen screen, CompletableFuture<UiWindow>[] opened) {
        super(app, dock -> {
            CompletableFuture<UiWindow> next = Ui.open(app, Objects.requireNonNull(screen, "screen"), dock);
            opened[0] = next;
            return next.thenApply(UiWindow::view);
        });
        this.screen = screen;
        whenOpen(view -> {
            CompletableFuture<UiWindow> next = opened[0];
            if (next != null) next.thenAccept(w -> {
                if (!window.complete(w)) window = CompletableFuture.completedFuture(w);
            });
        });
    }

    public Screen screen() {
        return screen;
    }

    /** The screen's window once it is open: its session, its refresh. */
    public CompletableFuture<UiWindow> window() {
        return window;
    }

    /** Draws the screen again: after JavaFX changed its fields. */
    public CompletableFuture<Void> refresh() {
        return window.thenCompose(UiWindow::refresh);
    }
}
