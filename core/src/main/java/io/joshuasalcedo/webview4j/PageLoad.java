package io.joshuasalcedo.webview4j;

/**
 * A view's page started or finished loading: every navigation, the first page too. {@code url} is where the page is
 * now, for an address bar.
 */
public record PageLoad(String view, State state, String url) implements WebViewEvent {

    public enum State { STARTED, FINISHED }
}
