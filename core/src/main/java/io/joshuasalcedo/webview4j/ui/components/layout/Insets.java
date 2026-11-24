package io.joshuasalcedo.webview4j.ui.components.layout;

import java.util.Map;

/** Space inside an edge, CSS pixels, as JavaFX's {@code Insets}: top, right, bottom, left. */
public record Insets(double top, double right, double bottom, double left) {

    public static final Insets EMPTY = new Insets(0, 0, 0, 0);

    /** The same on every side. */
    public Insets(double all) {
        this(all, all, all, all);
    }

    /** Above and below, then left and right. */
    public static Insets of(double vertical, double horizontal) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    Object write() {
        if (top == right && right == bottom && bottom == left) return top;
        return Map.of("top", top, "right", right, "bottom", bottom, "left", left);
    }
}
