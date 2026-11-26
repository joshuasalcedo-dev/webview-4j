package io.joshuasalcedo.webview4j.plugin.devtools;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.NetworkEvent;
import io.joshuasalcedo.webview4j.NetworkEvent.Phase;
import io.joshuasalcedo.webview4j.WebSocketFrame;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Network tab's rows: a request's steps joined by id, failures, resources, sockets, the filters. */
class NetworkModelTest {

    static NetworkEvent step(Phase phase, long id, String url, int status) {
        return new NetworkEvent("wv-1", phase, id, "fetch", "POST", url, 1_700_000_000_000L, status, "OK", 42, 1536, "application/json",
                null, Map.of("x-probe", "yes"), "{\"a\":1}", Map.of("content-type", "application/json"), "{\"ok\":true}");
    }

    @Test
    void aRequestsStepsAreOneRow() {
        NetworkModel m = new NetworkModel();
        m.accept(step(Phase.START, 1, "https://api.example.com/orders?page=2", 0));
        assertEquals("(pending)", m.getValueAt(0, 2));
        m.accept(step(Phase.END, 1, null, 201));
        assertEquals(1, m.getRowCount(), "start and end are one row");
        assertEquals("orders?page=2", m.getValueAt(0, 0));
        assertEquals("POST", m.getValueAt(0, 1));
        assertEquals("201", m.getValueAt(0, 2));
        assertEquals("1.5 kB", m.getValueAt(0, 4));
        assertEquals("42 ms", m.getValueAt(0, 5));
        assertEquals("{\"ok\":true}", m.row(0).responseBody);
        assertEquals("yes", m.row(0).requestHeaders.get("x-probe"));
    }

    @Test
    void failuresResourcesAndSocketsHaveTheirRows() {
        NetworkModel m = new NetworkModel();
        m.accept(step(Phase.START, 2, "https://down.example.com/x", 0));
        m.accept(new NetworkEvent("wv-1", Phase.ERROR, 2, null, null, null, 0, 0, null, 10, 0, null, "network error",
                Map.of(), null, Map.of(), null));
        assertEquals("(failed) network error", m.getValueAt(0, 2));
        assertEquals(NetworkModel.State.FAILED, m.row(0).state);

        // a resource's id may be a request's too: kept apart
        m.accept(new NetworkEvent("wv-1", Phase.RESOURCE, 2, "img", "GET", "https://cdn.example.com/logo.png", 1, 200, null, 5, 2048, null,
                null, Map.of(), null, Map.of(), null));
        assertEquals(2, m.getRowCount());

        m.accept(new WebSocketFrame("wv-1", 7, "wss://chat.example.com/live", WebSocketFrame.Direction.OPEN, null));
        m.accept(new WebSocketFrame("wv-1", 7, "wss://chat.example.com/live", WebSocketFrame.Direction.IN, "hello"));
        m.accept(new WebSocketFrame("wv-1", 7, "wss://chat.example.com/live", WebSocketFrame.Direction.OUT, "hi"));
        assertEquals(3, m.getRowCount());
        NetworkModel.Row socket = m.row(2);
        assertEquals("101 (open)", NetworkModel.status(socket));
        assertEquals(2, socket.frames.size());

        m.filter("", "WebSocket");
        assertEquals(1, m.getRowCount());
        m.filter("", "Resources");
        assertEquals("logo.png", m.getValueAt(0, 0));
        m.filter("down.example", "All");
        assertEquals(1, m.getRowCount());
        m.clear();
        assertEquals(0, m.getRowCount());
    }

    @Test
    void jsonBodiesAreLaidOut() {
        assertEquals("{\n  \"a\": 1,\n  \"b\": [\n    true\n  ]\n}", NetworkPanel.pretty("{\"a\":1,\"b\":[true]}"));
        assertEquals("plain text", NetworkPanel.pretty("plain text"));
    }
}
