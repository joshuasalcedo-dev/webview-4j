package io.joshuasalcedo.webview4j.ui.components;

/** A pill's colour. */
public enum PillColor {
    GREY, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, PINK;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }
}
