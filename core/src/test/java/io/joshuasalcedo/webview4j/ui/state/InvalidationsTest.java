package io.joshuasalcedo.webview4j.ui.state;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** A server telling the app what changed, over server-sent events; and the stream coming back after a drop. */
class InvalidationsTest {

    @Test
    void whatTheServerSaysChangedIsFetchedAgainAndTheStreamComesBack() throws Exception {
        BlockingQueue<String> toSend = new LinkedBlockingQueue<>();
        AtomicInteger connections = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webview4j/invalidations", exchange -> {
            connections.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(": listening\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                while (true) {
                    String next = toSend.poll(5, TimeUnit.SECONDS);
                    if (next == null || next.equals("DROP")) return;   // the connection drops
                    out.write(("data: " + next + "\n\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        try {
            AtomicInteger fetches = new AtomicInteger();
            ServerState<String> orders = ServerState.of("orders.open", String.class, () -> "orders " + fetches.incrementAndGet())
                    .property(Property.of().fresh(Duration.ofHours(1)));
            Session session = Session.of(new Object()).storage(StateStorage.memory());
            List<String> heard = new CopyOnWriteArrayList<>();
            session.onInvalidate(heard::add);
            Tracking.Reader shown = new Tracking.Reader(() -> {}, null);
            session.call(() -> Tracking.reading(shown, orders::data));
            StateTest.until(() -> fetches.get() == 1);

            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webview4j/invalidations");
            try (Invalidations listening = Invalidations.listen(session, uri)) {
                StateTest.until(listening::connected);
                toSend.add("people");
                toSend.add("orders.>");
                StateTest.until(() -> fetches.get() == 2);          // shown, so fetched again at once
                assertEquals(List.of("people", "orders.>"), heard);   // and the pages are told too

                toSend.add("DROP");
                StateTest.until(() -> connections.get() == 2);       // it connects again by itself
                StateTest.until(() -> fetches.get() == 3);            // and checks what refetches on reconnect
            }
        } finally {
            server.stop(0);
        }
    }
}
