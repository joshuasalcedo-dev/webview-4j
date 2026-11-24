package io.joshuasalcedo.webview4j.ui.components;

/** How much a log line matters. */
public enum LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }
}
