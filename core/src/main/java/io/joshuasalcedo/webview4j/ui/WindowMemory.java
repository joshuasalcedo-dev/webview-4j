package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Monitor;
import io.joshuasalcedo.webview4j.Moved;
import io.joshuasalcedo.webview4j.Resized;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.state.Codec;
import io.joshuasalcedo.webview4j.ui.state.Saved;
import io.joshuasalcedo.webview4j.ui.state.Session;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A window that opens where it was left ({@code WindowOptions.remember}): its place, size and whether it was
 * maximized, kept in the app's session (on disk) whenever it moves or resizes; on a monitor that is gone, it comes
 * back on the main one.
 */
public final class WindowMemory {

    /** Where a window was: its normal bounds (also while maximized), and whether it was maximized. */
    public record Place(double x, double y, double width, double height, boolean maximized) {}

    private WindowMemory() {}

    static Saved<Place> saved(String name) {
        return Saved.of("window." + name, Codec.of(Place.class), () -> null);
    }

    /** Opens the window at its remembered place, then keeps the place as it changes. */
    public static CompletableFuture<View> open(App app, Map<String, Object> payload, String name,
                                               Function<Map<String, Object>, CompletableFuture<View>> opener) {
        Session session = UiWindow.session(app);
        Saved<Place> saved = saved(name);
        Place was = session.call(saved::get);
        if (was != null) {
            payload.put("positioned", true);
            payload.put("x", was.x());
            payload.put("y", was.y());
            payload.put("width", was.width());
            payload.put("height", was.height());
            if (was.maximized()) payload.put("maximized", true);
        }
        return opener.apply(payload).thenCompose(view -> {
            CompletableFuture<Void> checked = was == null ? CompletableFuture.completedFuture(null) : onScreen(view, was);
            return checked.thenApply(v -> {
                keep(view, session, saved, was);
                return view;
            });
        });
    }

    /** On a monitor that is gone (or barely on one), the window comes back on the main monitor, centered. */
    private static CompletableFuture<Void> onScreen(View view, Place was) {
        return view.monitors().thenCompose(monitors -> {
            double best = monitors.stream().mapToDouble(m -> m.covers(was.x(), was.y(), was.width(), was.height())).max().orElse(0);
            if (monitors.isEmpty() || best >= 0.25) return CompletableFuture.completedFuture(null);
            Monitor main = monitors.stream().filter(Monitor::primary).findFirst().orElse(monitors.getFirst());
            double w = Math.min(was.width(), main.width() * 0.9);
            double h = Math.min(was.height(), main.height() * 0.9);
            return view.setBounds((int) Math.round(main.x() + (main.width() - w) / 2), (int) Math.round(main.y() + (main.height() - h) / 2),
                    (int) Math.round(w), (int) Math.round(h));
        });
    }

    /** As it moves and resizes: its normal bounds kept (not the maximized ones), once a burst of changes settles. */
    private static void keep(View view, Session session, Saved<Place> saved, Place was) {
        AtomicBoolean queued = new AtomicBoolean();
        Place[] last = {was};
        Runnable changed = () -> {
            if (!queued.compareAndSet(false, true)) return;
            Thread.ofVirtual().name("webview4j-window-memory").start(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    return;
                } finally {
                    queued.set(false);
                }
                view.geometry().thenAccept(g -> {
                    if (g.minimized() || g.fullscreen()) return;
                    Place next = g.maximized()
                            ? (last[0] == null ? new Place(g.x(), g.y(), g.width(), g.height(), true)
                                : new Place(last[0].x(), last[0].y(), last[0].width(), last[0].height(), true))
                            : new Place(g.x(), g.y(), g.width(), g.height(), false);
                    last[0] = next;
                    session.call(() -> {
                        saved.set(next);
                        return null;
                    });
                });
            });
        };
        view.subscribe(Moved.class, e -> changed.run());
        view.subscribe(Resized.class, e -> changed.run());
        changed.run();
    }

    /** Where a remembered window is kept, for a settings page that offers to forget it. */
    public static void forget(App app, String name) {
        UiWindow.session(app).call(() -> {
            saved(name).reset();
            return null;
        });
    }

    /** The monitors of a window, for a screen that offers to move it to another ({@link View#monitors()}). */
    public static CompletableFuture<List<Monitor>> monitors(View view) {
        return view.monitors();
    }

}
