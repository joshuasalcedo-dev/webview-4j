package io.joshuasalcedo.webview4j.ui.components;

/** Light or dark: the system's (and as it changes), or one of them. */
public enum ColorScheme {
    SYSTEM, LIGHT, DARK;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }
}
