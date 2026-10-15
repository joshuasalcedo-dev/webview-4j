package io.joshuasalcedo.webview4j;

/**
 * What a view reports from its page. Only {@link #CONSOLE} is on unless the
 * {@link WindowOptions#capture window options} or {@link View#capture(Capture, boolean)} say otherwise:
 * the rest can carry request bodies, cookies and what the user types.
 */
public enum Capture {
    /** {@link ApiCall}: every fetch and XMLHttpRequest. */
    API("api"),
    /** {@link Click}: clicks on buttons, links and elements with a role. */
    CLICKS("clicks"),
    /** {@link DomEvent}: clicks, hovers, input, focus and keys, with element snapshots. */
    DOM_EVENTS("domEvents"),
    /** {@link WebSocketFrame}: the page's WebSockets, and sending on them. */
    WEBSOCKET("websocket"),
    /** Page {@link LogRecord}s: {@code console.*} and uncaught errors. */
    CONSOLE("console");

    private final String key;

    Capture(String key) {
        this.key = key;
    }

    /** The name the native side knows it by, e.g. {@code domEvents}. */
    String key() {
        return key;
    }
}
