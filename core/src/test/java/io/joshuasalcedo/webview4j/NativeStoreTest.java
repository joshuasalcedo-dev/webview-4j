package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.ui.state.StateStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The redb store through FFM, and the in-memory one: the same answers. */
class NativeStoreTest {

    @TempDir
    Path dir;

    @Test
    void keepsBytesByKeyAndAfterOpeningAgain() {
        Path file = dir.resolve("app").resolve("state.redb");
        try (NativeStore store = NativeStore.open(file)) {
            behaves(store);
        }
        assertTrue(Files.exists(file));
        try (NativeStore again = NativeStore.open(file)) {
            assertEquals("two", text(again.get("orders.2").orElseThrow()), "still there after opening again");
            assertTrue(again.get("orders.1").isEmpty(), "deleted stays deleted");
        }
    }

    @Test
    void theMemoryStoreAnswersTheSame() {
        behaves(StateStorage.memory());
    }

    @Test
    void bigValuesAndManyKeys() {
        try (NativeStore store = NativeStore.open(dir.resolve("big.redb"))) {
            byte[] big = new byte[5_000_000];
            for (int i = 0; i < big.length; i++) big[i] = (byte) i;
            store.put("big", big);
            assertArrayEquals(big, store.get("big").orElseThrow());
            for (int i = 0; i < 500; i++) store.put("many." + String.format("%03d", i), new byte[] {(byte) i});
            assertEquals(500, store.keys("many.").size());
        }
    }

    @Test
    void theAppsPlaceIsTheSystems() {
        Path place = NativeStore.directory("Notes: an app");
        assertTrue(place.toString().endsWith("Notes_ an app"), place.toString());
        assertTrue(place.isAbsolute());
    }

    static void behaves(StateStorage store) {
        store.put("orders.1", bytes("one"));
        store.put("orders.2", bytes("two"));
        store.put("people", new byte[0]);
        store.put("ordersx", bytes("not under orders."));
        assertEquals("one", text(store.get("orders.1").orElseThrow()));
        assertArrayEquals(new byte[0], store.get("people").orElseThrow());
        assertTrue(store.get("nobody").isEmpty());
        assertEquals(List.of("orders.1", "orders.2"), store.keys("orders."));
        assertTrue(store.delete("orders.1"));
        assertFalse(store.delete("orders.1"));
        store.put("orders.2", bytes("two"));
        assertEquals(List.of("orders.2"), store.keys("orders."));
    }

    static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String text(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
