package io.joshuasalcedo.webview4j.plugin.mdi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where an {@link Mdi}'s windows are, back to front (the last on top): to keep a workspace and put it back. Kept by
 * {@link Mdi#remember} on its own, or by a screen ({@link Mdi#onLayoutChange}, {@link Mdi#layout}).
 */
public record MdiLayout(List<Place> windows) {

    /** One window: its place in the area (CSS pixels) and how it is shown. */
    public record Place(String id, double x, double y, double width, double height, MdiWindow.State state) {}

    public MdiLayout {
        windows = List.copyOf(windows == null ? List.of() : windows);
    }

    public static MdiLayout empty() {
        return new MdiLayout(List.of());
    }

    public Optional<Place> of(String id) {
        return windows.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** The window on top that is not minimized. */
    public Optional<String> active() {
        for (int i = windows.size() - 1; i >= 0; i--) {
            if (windows.get(i).state() != MdiWindow.State.MINIMIZED) return Optional.of(windows.get(i).id());
        }
        return Optional.empty();
    }

    List<Map<String, Object>> write() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Place p : windows) {
            out.add(Map.of("id", p.id(), "x", p.x(), "y", p.y(), "width", p.width(), "height", p.height(),
                    "state", p.state().name().toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    static MdiLayout read(Object value) {
        List<Place> places = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                places.add(new Place(String.valueOf(m.get("id")), number(m.get("x")), number(m.get("y")), number(m.get("width")),
                        number(m.get("height")), MdiWindow.State.valueOf(String.valueOf(m.get("state")).toUpperCase(Locale.ROOT))));
            }
        }
        return new MdiLayout(places);
    }

    private static double number(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
