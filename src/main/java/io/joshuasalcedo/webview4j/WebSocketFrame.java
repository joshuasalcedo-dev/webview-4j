package io.joshuasalcedo.webview4j;

/**
 * Something on one of the page's WebSockets ({@link Capture#WEBSOCKET}). {@code socket} numbers the
 * page's sockets; send on one with {@link WebSocketPublisher#send}. {@code data} is the text frame
 * for {@code IN} and {@code OUT}, null for {@code OPEN} and {@code CLOSE}. Binary frames are not reported.
 */
public record WebSocketFrame(String view, long socket, String url, Direction direction, String data)
        implements WebViewEvent {

    public enum Direction { OPEN, IN, OUT, CLOSE }
}
