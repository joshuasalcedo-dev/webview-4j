package io.joshuasalcedo.webview4j.spring.state;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.ui.state.Invalidations;
import io.joshuasalcedo.webview4j.ui.state.Session;
import io.joshuasalcedo.webview4j.ui.state.StateStorage;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

/** A Spring server publishes what changed; a client listening at /webview4j/invalidations hears it. */
@SpringBootTest(classes = StateInvalidationsTest.Server.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "webview4j.enabled=false")
@DirtiesContext
class StateInvalidationsTest {

    @SpringBootApplication(scanBasePackages = "nothing")
    static class Server {}

    @LocalServerPort
    int port;

    @Autowired
    StateInvalidations invalidations;

    @Test
    void theClientsHearWhatChanged() throws Exception {
        Session client = Session.of(new Object()).storage(StateStorage.memory());
        List<String> heard = new CopyOnWriteArrayList<>();
        client.onInvalidate(heard::add);
        try (Invalidations listening = Invalidations.listen(client, URI.create("http://127.0.0.1:" + port + StateInvalidations.PATH))) {
            for (int i = 0; i < 200 && invalidations.listeners() == 0; i++) Thread.sleep(10);
            assertEquals(1, invalidations.listeners());
            invalidations.publish("orders.>", "people");
            for (int i = 0; i < 200 && heard.size() < 2; i++) Thread.sleep(10);
            assertEquals(List.of("orders.>", "people"), heard);
        }
    }
}
