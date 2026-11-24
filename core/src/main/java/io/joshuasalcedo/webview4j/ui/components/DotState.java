package io.joshuasalcedo.webview4j.ui.components;

/** A connection's state, as a coloured light. */
public enum DotState {
    ONLINE, CONNECTING, OFFLINE, ERROR;

    /** As the page's renderer names it: {@code largeTitle} for LARGE_TITLE. */
    public String key() {
        return Keys.camel(this);
    }
}
