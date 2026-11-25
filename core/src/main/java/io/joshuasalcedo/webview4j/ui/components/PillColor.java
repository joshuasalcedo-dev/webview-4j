package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.JsonValue;

/** A pill's colour; also a tag's, in the chat's data (java-gen uses this one, not a copy). */
public enum PillColor implements JsonValue {
    GREY, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, PINK,
    /** The system's accent colour. */
    ACCENT;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }

    /** In data sent to the page (a tag's colour), the page's own word for it. */
    @Override
    public Object json() {
        return key();
    }
}
