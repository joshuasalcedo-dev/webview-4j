package io.joshuasalcedo.webview4j.ui.components.layout;

import java.util.Locale;

/** Left, center or right in a cell, as JavaFX's {@code HPos}. */
public enum HPos {
    LEFT, CENTER, RIGHT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
