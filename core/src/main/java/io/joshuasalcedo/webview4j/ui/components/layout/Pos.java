package io.joshuasalcedo.webview4j.ui.components.layout;

import java.util.Locale;

/** Where something sits in its area, as JavaFX's {@code Pos}: up and down, then left and right. */
public enum Pos {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    BASELINE_LEFT, BASELINE_CENTER, BASELINE_RIGHT;

    /** As the kit names it: {@code "top-left"}, {@code "center"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
