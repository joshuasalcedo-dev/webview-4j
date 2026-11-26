package io.joshuasalcedo.swaswa.ffi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Where each window opened with {@link WindowOptions#remember(String)} was, kept between runs: its normal bounds and
 * whether it was maximized, in its own store beside the app's data ({@code windows.redb}). A window opens there again,
 * or on the main monitor when that one is gone.
 */
final class WindowPlaces {

    /** Where a window was: its normal bounds (also while maximized), and whether it was maximized. */
    record Place(double x, double y, double width, double height, boolean maximized) {}

    private static NativeStore store;

    private WindowPlaces() {}

    /** The app's name for its data folder: {@code -Dswaswa.app}, else its main class's simple name or its jar's. */
    static String appName() {
        String named = System.getProperty("swaswa.app");
        if (named != null && !named.isBlank()) return named;
        String command = System.getProperty("sun.java.command", "");
        String main = command.isBlank() ? "swaswa-app" : command.split(" ")[0];
        if (main.endsWith(".jar")) main = Path.of(main).getFileName().toString().replaceAll("\\.jar$", "");
        return main.contains(".") ? main.substring(main.lastIndexOf('.') + 1) : main;
    }

    private static synchronized NativeStore store() {
        if (store == null) store = NativeStore.open(NativeStore.directory(appName()).resolve("windows.redb"));
        return store;
    }

    static Place read(String name) {
        try {
            return store().get(key(name)).map(bytes -> {
                Map<String, Object> m = Json.object(new String(bytes, StandardCharsets.UTF_8));
                return new Place(number(m, "x"), number(m, "y"), number(m, "width"), number(m, "height"), Boolean.TRUE.equals(m.get("maximized")));
            }).orElse(null);
        } catch (RuntimeException e) {
            return null; // a place that cannot be read is no place: the window opens where its options say
        }
    }

    static void write(String name, Place place) {
        store().put(key(name), Json.write(place).getBytes(StandardCharsets.UTF_8));
    }

    /** Forgets where {@code name} was. */
    static void forget(String name) {
        store().delete(key(name));
    }

    private static String key(String name) {
        return "window." + name;
    }

    private static double number(Map<String, Object> m, String key) {
        return m.get(key) instanceof Number n ? n.doubleValue() : 0;
    }

    /** Opens the window at its remembered place ({@code payload} changed first), then keeps its place as it changes. */
    static CompletableFuture<View> open(App app, Map<String, Object> payload, String name,
                                        Function<Map<String, Object>, CompletableFuture<View>> opener) {
        Place was = read(name);
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
                keep(view, name, was);
                return view;
            });
        });
    }

    /** On a monitor that is gone (or barely on one), the window comes back on the main monitor, centered. */
    private static CompletableFuture<Void> onScreen(View view, Place was) {
        return view.monitors().thenCompose(monitors -> {
            double best = monitors.stream().mapToDouble(m -> m.covers(was.x(), was.y(), was.width(), was.height())).max().orElse(0);
            if (monitors.isEmpty() || best >= 0.25) return CompletableFuture.completedFuture(null);
            List<Monitor> all = monitors;
            Monitor main = all.stream().filter(Monitor::primary).findFirst().orElse(all.getFirst());
            double w = Math.min(was.width(), main.width() * 0.9);
            double h = Math.min(was.height(), main.height() * 0.9);
            return view.setBounds((int) Math.round(main.x() + (main.width() - w) / 2), (int) Math.round(main.y() + (main.height() - h) / 2),
                    (int) Math.round(w), (int) Math.round(h));
        });
    }

    /** As it moves and resizes: its normal bounds kept (not the maximized ones), once a burst of changes settles. */
    private static void keep(View view, String name, Place was) {
        AtomicBoolean queued = new AtomicBoolean();
        Place[] last = {was};
        Runnable changed = () -> {
            if (!queued.compareAndSet(false, true)) return;
            Thread.ofVirtual().name("swaswa-window-places").start(() -> {
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
                    write(name, next);
                });
            });
        };
        view.subscribe(Moved.class, e -> changed.run());
        view.subscribe(Resized.class, e -> changed.run());
        changed.run();
    }
}
