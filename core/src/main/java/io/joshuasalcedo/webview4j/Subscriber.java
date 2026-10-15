package io.joshuasalcedo.webview4j;

/**
 * Receives events of one kind. Each subscriber gets its events in order, one at a time, on a
 * virtual thread of its own: never on the event loop, so it may block and may perform actions.
 * An exception it throws is logged and the next event still comes.
 */
@FunctionalInterface
public interface Subscriber<E extends WebViewEvent> {

    void on(E event) throws Exception;
}
