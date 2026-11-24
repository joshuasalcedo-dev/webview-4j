package io.joshuasalcedo.webview4j.ui.components;

/** Which system's look: the one it runs on, Windows 11, or macOS. */
public enum Platform {
    AUTO, WINDOWS, MAC;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }
}
