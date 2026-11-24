package io.joshuasalcedo.webview4j.ui.components;

/** A text's colour, by what it says. */
public enum Tone {
    DEFAULT, SECONDARY, TERTIARY, ACCENT, SUCCESS, WARNING, ERROR;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }
}
