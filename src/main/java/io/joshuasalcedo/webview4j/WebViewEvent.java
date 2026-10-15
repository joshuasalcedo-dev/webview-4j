package io.joshuasalcedo.webview4j;

/**
 * Something the native side reported. Subscribe to one kind with
 * {@link App#subscribe(Class, Subscriber)}, or to all with {@code WebViewEvent.class}.
 *
 * <p>Page reports ({@link ApiCall}, {@link Click}, {@link DomEvent}, {@link WebSocketFrame}, and
 * page {@link LogRecord}s) arrive only while switched on for their view: see {@link Capture}.
 */
public sealed interface WebViewEvent permits Ready, Closed, ApiCall, Click, DomEvent, WebSocketFrame, LogRecord {

    /** The view the event happened in, or null when it is not about one view. */
    default String view() {
        return null;
    }
}
