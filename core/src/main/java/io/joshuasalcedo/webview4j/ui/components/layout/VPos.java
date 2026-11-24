package io.joshuasalcedo.webview4j.ui.components.layout;

import java.util.Locale;

/** Top, center, bottom or on the text's baseline in a cell, as JavaFX's {@code VPos}. */
public enum VPos {
    TOP, CENTER, BOTTOM, BASELINE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
