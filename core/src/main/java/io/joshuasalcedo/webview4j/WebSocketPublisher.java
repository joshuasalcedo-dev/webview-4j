package io.joshuasalcedo.webview4j;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Sends text frames on a view's WebSockets, as if the page had sent them, and subscribes to what
 * crosses them. Needs {@link Capture#WEBSOCKET} on before the page opens its sockets: only sockets
 * opened while capturing are numbered.
 * <pre>{@code
 * view.websocket().subscribe(frame -> {
 *     if (frame.direction() == Direction.OPEN) view.websocket().send(frame.socket(), "{\"op\":\"hello\"}");
 * });
 * }</pre>
 */
public final class WebSocketPublisher {

    private final View view;

    WebSocketPublisher(View view) {
        this.view = view;
    }

    /** Sends {@code data} on socket number {@code socket}; fails if it is unknown or not open. */
    public CompletableFuture<Void> send(long socket, String data) {
        Objects.requireNonNull(data, "data");
        return view.app().submit(WebViewAction.WEBSOCKET_SEND, view.id(), Map.of("socket", socket, "data", data))
                .thenApply(reply -> null);
    }

    /** Every frame, open and close on this view's sockets. */
    public Subscription subscribe(Subscriber<? super WebSocketFrame> subscriber) {
        return view.subscribe(WebSocketFrame.class, subscriber);
    }
}
