package io.joshuasalcedo.webview4j.spring.state;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The server's side of webview-4j's invalidations: the desktop clients (and anything else) listen at
 * {@code /webview4j/invalidations}, a stream of server-sent events; {@link #publish} tells them what changed, so they
 * fetch only that again. A {@code @WebViewApplication} client listens by itself (webview4j.invalidations).
 * <pre>{@code
 * @PostMapping("/orders/{id}/ship")
 * void ship(@PathVariable String id) {
 *     orders.ship(id);
 *     invalidations.publish("orders.>");
 * }
 * }</pre>
 */
@RestController
public class StateInvalidations {

    /** Where the stream is. */
    public static final String PATH = "/webview4j/invalidations";

    private final List<SseEmitter> listeners = new CopyOnWriteArrayList<>();

    @GetMapping(path = PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter listen() {
        SseEmitter emitter = new SseEmitter(0L);   // open until the client goes
        listeners.add(emitter);
        emitter.onCompletion(() -> listeners.remove(emitter));
        emitter.onTimeout(() -> listeners.remove(emitter));
        emitter.onError(e -> listeners.remove(emitter));
        try {
            emitter.send(SseEmitter.event().comment("listening"));
        } catch (IOException e) {
            listeners.remove(emitter);
        }
        return emitter;
    }

    /** Tells every listener that what matches these patterns changed ({@code "orders"}, {@code "orders.>"}). */
    public void publish(String... patterns) {
        for (String pattern : patterns) {
            for (SseEmitter emitter : listeners) {
                try {
                    emitter.send(SseEmitter.event().data(pattern));
                } catch (IOException | IllegalStateException e) {
                    listeners.remove(emitter);
                }
            }
        }
    }

    /** How many listen now. */
    public int listeners() {
        return listeners.size();
    }
}
