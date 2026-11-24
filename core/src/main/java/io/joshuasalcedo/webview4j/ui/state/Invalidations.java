package io.joshuasalcedo.webview4j.ui.state;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * A server telling the app what changed, so it fetches only that: a stream of server-sent events, each one's data a
 * pattern of keys ({@code orders}, {@code orders.>}). Every event invalidates the session's matching server state
 * (and its pages' queries); when the stream comes back after a drop, what has {@code refetchOnReconnect} is checked.
 * <pre>{@code
 * Invalidations.listen(session, URI.create("https://office.example.com/webview4j/invalidations"));
 * }</pre>
 * On the server, the Spring Boot starter has the endpoint ({@code StateInvalidations.publish("orders.>")}); any
 * server can send {@code data: orders.>\n\n} on a {@code text/event-stream}.
 */
public final class Invalidations implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(Invalidations.class.getName());

    private final Session session;
    private final URI uri;
    private final UnaryOperator<HttpRequest.Builder> request;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile boolean closed;
    private volatile boolean connected;
    private final Thread thread;

    private Invalidations(Session session, URI uri, UnaryOperator<HttpRequest.Builder> request) {
        this.session = Objects.requireNonNull(session, "session");
        this.uri = Objects.requireNonNull(uri, "uri");
        this.request = Objects.requireNonNull(request, "request");
        this.thread = Thread.ofVirtual().name("webview4j-invalidations").start(this::run);
    }

    /** Listens to {@code uri} until closed, connecting again (waiting longer each time) when it drops. */
    public static Invalidations listen(Session session, URI uri) {
        return new Invalidations(session, uri, b -> b);
    }

    /** ... with headers of its own (a token: {@code b -> b.header("Authorization", "Bearer " + token)}). */
    public static Invalidations listen(Session session, URI uri, UnaryOperator<HttpRequest.Builder> request) {
        return new Invalidations(session, uri, request);
    }

    /** Whether the stream is open now. */
    public boolean connected() {
        return connected;
    }

    private void run() {
        Duration wait = Duration.ofSeconds(1);
        boolean droppedBefore = false;
        while (!closed) {
            try {
                HttpRequest req = request.apply(HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET()).build();
                HttpResponse<java.io.InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() / 100 != 2) throw new IllegalStateException("the invalidations answered " + res.statusCode());
                connected = true;
                wait = Duration.ofSeconds(1);
                if (droppedBefore) session.call(() -> {
                    ServerState.reconnected(session);
                    return null;
                });
                try (BufferedReader in = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                    StringBuilder data = new StringBuilder();
                    for (String line; !closed && (line = in.readLine()) != null; ) {
                        if (line.isEmpty()) {
                            if (!data.isEmpty()) event(data.toString());
                            data.setLength(0);
                        } else if (line.startsWith("data:")) {
                            if (!data.isEmpty()) data.append('\n');
                            data.append(line.substring(5).strip());
                        }
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                if (!closed) LOG.log(System.Logger.Level.DEBUG, "the invalidations at " + uri + " dropped", e);
            }
            connected = false;
            droppedBefore = true;
            if (closed) return;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                return;
            }
            wait = wait.multipliedBy(2).compareTo(Duration.ofSeconds(30)) > 0 ? Duration.ofSeconds(30) : wait.multipliedBy(2);
        }
    }

    private void event(String data) {
        for (String pattern : data.split("\n")) {
            String p = pattern.strip();
            if (p.isEmpty()) continue;
            session.call(() -> {
                ServerState.invalidateHere(session, p);
                session.invalidated(p);
                return null;
            });
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
    }
}
